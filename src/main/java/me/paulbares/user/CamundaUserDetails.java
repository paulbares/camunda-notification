package me.paulbares.user;

import java.io.Serializable;
import java.util.Collection;

/**
 * Information about a user that needs to interacts with the Camunda engine.
 */
public interface CamundaUserDetails extends Serializable {

  /**
   * Returns the user identifier.
   *
   * @return the user identifier. It cannot be null.
   */
  String getUser();

  /**
   * Returns the groups the user belongs to.
   *
   * @return the groups the user belongs to. It cannot be null.
   */
  Collection<String> getGroups();
}
