package scala.tools.eclipse.contribution.weaving.jdt.jcompiler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.PlatformObject;

/**
 * A resource delta which allows addition of a Resource change in its tree, and 
 * use wrapped IResourceDelta for other nodes.<br>
 * It is used to tell the Eclipse Java compiler to compile more file than the set
 * that was marked at the beginning of the build.
 */
public class ExpandableResourceDelta extends PlatformObject implements IResourceDelta {

  /**
   * A wrapped resource. If it is set, it is used for all information except
   * the list of children.
   */
  private IResourceDelta wrapped;
  
  /**
   * The children of this resource delta.
   */
  private List<ExpandableResourceDelta> children= new ArrayList<ExpandableResourceDelta>();
  
  /**
   * A resource. If it is set, this resource delta is content change for this resource.
   */
  private IResource resource;

  /**
   * 
   * @param resourceDelta
   */
  private ExpandableResourceDelta(IResourceDelta resourceDelta) {
    wrapped= resourceDelta;
  }
  
  private ExpandableResourceDelta(IResource resource) {
    this.resource= resource;
  }

  public void accept(IResourceDeltaVisitor visitor) throws CoreException {
    accept(visitor, IResource.NONE);
  }

  public void accept(IResourceDeltaVisitor visitor, boolean includePhantoms)
      throws CoreException {
    accept(visitor, includePhantoms ? IContainer.INCLUDE_PHANTOMS : IResource.NONE);
  }

  public void accept(IResourceDeltaVisitor visitor, int memberFlags)
      throws CoreException {
    for (IResourceDelta child: getAffectedChildren(ADDED | REMOVED | CHANGED, memberFlags)) {
      visitor.visit(child);
    }
  }

  public IResourceDelta findMember(IPath path) {
    if (path.segmentCount() == 0)
      return this;
    
    // look for a child with the first segment of the path.
    IPath fullPath= getFullPath().append(path.segment(0));
    int nbSegments= fullPath.segmentCount();
    for (IResourceDelta child: children) {
      if (child.getFullPath().matchingFirstSegments(fullPath) == nbSegments) {
        // and search in this child
        return child.findMember(path.removeFirstSegments(1));
      }
    }
    return null;
  }

  public IResourceDelta[] getAffectedChildren() {
    return getAffectedChildren(ADDED | REMOVED | CHANGED, IResource.NONE);
  }

  public IResourceDelta[] getAffectedChildren(int kindMask) {
    return getAffectedChildren(kindMask, IResource.NONE);
  }

  public IResourceDelta[] getAffectedChildren(int kindMask, int memberFlags) {
    List<IResourceDelta> affectedChildren= new ArrayList<IResourceDelta>();
    if ((memberFlags & IContainer.INCLUDE_PHANTOMS) != 0) {
      kindMask |= ADDED_PHANTOM | REMOVED_PHANTOM;
    }
    boolean includeHidden = (memberFlags & IContainer.INCLUDE_HIDDEN) != 0;
    boolean includeTeamPrivateMember = (memberFlags & IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS) == 0;
    
    for (IResourceDelta child: children) {
      if ((kindMask & child.getKind()) != 0 && (includeHidden || !child.getResource().isHidden()) && (includeTeamPrivateMember || !child.getResource().isTeamPrivateMember())) {
        affectedChildren.add(child);
      }
    }
    
    return affectedChildren.toArray(new IResourceDelta[affectedChildren.size()]);
  }

  public int getFlags() {
    if (wrapped != null) 
      return wrapped.getFlags();
    return IResourceDelta.CONTENT;
  }

  public IPath getFullPath() {
    if (wrapped != null) 
      return wrapped.getFullPath();
    return resource.getFullPath();
  }

  public int getKind() {
    if (wrapped != null)
      return wrapped.getKind();
    return IResourceDelta.CHANGED;
  }

  public IMarkerDelta[] getMarkerDeltas() {
    if (wrapped != null)
      return wrapped.getMarkerDeltas();
    return new IMarkerDelta[0];
  }

  public IPath getMovedFromPath() {
    if (wrapped != null)
      return wrapped.getMovedFromPath();
    return null;
  }

  public IPath getMovedToPath() {
    if (wrapped != null)
      return wrapped.getMovedToPath();
    return null;
  }

  public IPath getProjectRelativePath() {
    if (wrapped != null) 
      return wrapped.getProjectRelativePath();
    return resource.getFullPath().makeRelativeTo(resource.getProject().getFullPath());
  }

  public IResource getResource() {
    if (wrapped != null) 
      return wrapped.getResource();
    return resource;
  }
  
  public void addChangedResource(IResource changedResource) {
    getOrAddResource(changedResource);
  }
  
  private ExpandableResourceDelta getOrAddResource(IResource newResource) {
    if (getResource().equals(newResource)) {
      return this;
    }
    ExpandableResourceDelta parentResourceDelta= getOrAddResource(newResource.getParent());
    for (ExpandableResourceDelta childResourceDelta: parentResourceDelta.children) {
      if (childResourceDelta.getResource().equals(newResource)) {
        return childResourceDelta;
      }
    }
    ExpandableResourceDelta newResourceDelta= new ExpandableResourceDelta(newResource);
    parentResourceDelta.children.add(newResourceDelta);
    return newResourceDelta;
  }

  public static ExpandableResourceDelta duplicate(IResourceDelta original) {
    ExpandableResourceDelta newDelta = new ExpandableResourceDelta(original);
    for (IResourceDelta child: original.getAffectedChildren(ALL_WITH_PHANTOMS, IContainer.INCLUDE_HIDDEN | IContainer.INCLUDE_PHANTOMS | IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS)) {
      newDelta.children.add(duplicate(child));
    }
    return newDelta;
  }


  @Override
  public String toString() {
    return "ExtendedResourceDelta(" + getFullPath() + ")";
  }
}