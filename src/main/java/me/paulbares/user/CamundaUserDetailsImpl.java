package me.paulbares.user;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Basic implementation of {@link CamundaUserDetails}.
 */
public class CamundaUserDetailsImpl implements CamundaUserDetails {

  /**
   * the name of the user.
   */
  protected final String user;

  /**
   * the groups the user belongs to.
   */
  protected final List<String> groups;

  /**
   * Constructor.
   *
   * @param user the name of the user.
   */
  public CamundaUserDetailsImpl(String user) {
    this(user, Collections.emptyList());
  }

  /**
   * Constructor.
   *
   * @param user the name of the user.
   * @param groups the groups the user belongs to.
   */
  public CamundaUserDetailsImpl(String user, List<String> groups) {
    Objects.requireNonNull(user);
    Objects.requireNonNull(groups);
    this.user = user;
    this.groups = groups;
  }

  @Override
  public String getUser() {
    return this.user;
  }

  @Override
  public List<String> getGroups() {
    return this.groups;
  }

  @Override
  public String toString() {
    return "CamundaUserDetailsImpl{"
            + "user='" + user + '\''
            + ", groups=" + groups
            + '}';
  }
}
