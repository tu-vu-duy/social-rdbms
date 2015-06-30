/*
 * Copyright (C) 2003-2015 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.addons.concurrency;

import java.util.List;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.addons.storage.dao.ActivityDAO;
import org.exoplatform.social.addons.storage.entity.Activity;
import org.exoplatform.social.addons.test.BaseCoreTest;
import org.exoplatform.social.addons.updater.ActivityMigrationService;
import org.exoplatform.social.addons.updater.ProfileMigrationService;
import org.exoplatform.social.addons.updater.RDBMSMigrationManager;
import org.exoplatform.social.addons.updater.RelationshipMigrationService;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.social.core.relationship.model.Relationship.Type;
import org.exoplatform.social.core.storage.impl.ActivityStorageImpl;
import org.exoplatform.social.core.storage.impl.RelationshipStorageImpl;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * Jun 19, 2015  
 */
public class AsynMigrationTest extends BaseCoreTest {
  protected final Log LOG = ExoLogger.getLogger(AsynMigrationTest.class);
  private ActivityStorageImpl jcrStorage;
  private RelationshipStorageImpl relationshipStorageImpl;
  private ActivityMigrationService activityMigration;
  private ProfileMigrationService profileMigration;
  private RelationshipMigrationService relationshipMigration;
  private RDBMSMigrationManager rdbmsMigrationManager;
  
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    jcrStorage = getService(ActivityStorageImpl.class);
    relationshipStorageImpl = getService(RelationshipStorageImpl.class);
    activityMigration = getService(ActivityMigrationService.class);
    relationshipMigration = getService(RelationshipMigrationService.class);
    profileMigration = getService(ProfileMigrationService.class);
    rdbmsMigrationManager = new RDBMSMigrationManager(profileMigration, relationshipMigration, activityMigration);
  }

  @Override
  public void tearDown() throws Exception {
    begin();
    ActivityDAO dao = getService(ActivityDAO.class);
    //
    List<Activity> items = dao.findAll();
    for (Activity item : items) {
      dao.delete(item);
    }
    super.tearDown();
  }
  
  public void testMigrationActivities() throws Exception {
    // create jcr data
    LOG.info("Create connection for root,john,mary and demo");
    //John invites Demo
    Relationship johnToDemo = new Relationship(johnIdentity, demoIdentity, Type.PENDING);
    relationshipStorageImpl.saveRelationship(johnToDemo);
    
    //John invites Mary
    Relationship johnToMary = new Relationship(johnIdentity, maryIdentity, Type.PENDING);
    relationshipStorageImpl.saveRelationship(johnToMary);
    
    //John invites Root
    Relationship johnToRoot = new Relationship(johnIdentity, rootIdentity, Type.PENDING);
    relationshipStorageImpl.saveRelationship(johnToRoot);
    
    //Root invites Mary
    Relationship rootToMary = new Relationship(rootIdentity, maryIdentity, Type.PENDING);
    relationshipStorageImpl.saveRelationship(rootToMary);
    
    //Demo invites Mary
    Relationship demoToMary = new Relationship(demoIdentity, maryIdentity, Type.PENDING);
    relationshipStorageImpl.saveRelationship(demoToMary);
    
    //Demo invites Root
    Relationship demoToRoot = new Relationship(demoIdentity, rootIdentity, Type.PENDING);
    relationshipStorageImpl.saveRelationship(demoToRoot);
    
    
    //confirmed john and demo
    johnToDemo.setStatus(Type.CONFIRMED);
    relationshipStorageImpl.saveRelationship(johnToDemo);
    
    //confirmed john and demo
    johnToMary.setStatus(Type.CONFIRMED);
    relationshipStorageImpl.saveRelationship(johnToMary);
    
    //confirmed john and root
    johnToRoot.setStatus(Type.CONFIRMED);
    relationshipStorageImpl.saveRelationship(johnToRoot);
    
    //confirmed root and mary
    rootToMary.setStatus(Type.CONFIRMED);
    relationshipStorageImpl.saveRelationship(rootToMary);
    
    //confirmed demo and mary
    demoToMary.setStatus(Type.CONFIRMED);
    relationshipStorageImpl.saveRelationship(demoToMary);
    
    //confirmed demo and root
    demoToRoot.setStatus(Type.CONFIRMED);
    relationshipStorageImpl.saveRelationship(demoToRoot);
    
    //
    LOG.info("Create the activities storage on JCR ....");
    createActivityToOtherIdentity(rootIdentity, johnIdentity, 5);
    createActivityToOtherIdentity(demoIdentity, maryIdentity, 5);
    createActivityToOtherIdentity(johnIdentity, demoIdentity, 5);
    createActivityToOtherIdentity(maryIdentity, rootIdentity, 5);
    LOG.info("Done created the activities storage on JCR.");
    RequestLifeCycle.end();
    RequestLifeCycle.begin(PortalContainer.getInstance());
    //
    rdbmsMigrationManager.start();
    //
    rdbmsMigrationManager.getMigrater().await();
    //
    assertEquals(20, activityStorage.getActivityFeed(rootIdentity, 0, 100).size());
    assertEquals(20, activityStorage.getActivityFeed(maryIdentity, 0, 100).size());
    assertEquals(20, activityStorage.getActivityFeed(johnIdentity, 0, 100).size());
    assertEquals(20, activityStorage.getActivityFeed(demoIdentity, 0, 100).size());
  }
  
  private void createActivityToOtherIdentity(Identity posterIdentity, Identity targetIdentity, int number) {
    List<ExoSocialActivity> activities = listOf(number, targetIdentity, posterIdentity, false, false);
    for (ExoSocialActivity activity : activities) {
      try {
        activity = jcrStorage.saveActivity(targetIdentity, activity);
        //
        List<ExoSocialActivity> comments = listOf(3, targetIdentity, posterIdentity, true, false);
        for (ExoSocialActivity comment : comments) {
          comment.setTitle("comment of " + posterIdentity.getId());
          //
          jcrStorage.saveComment(activity, comment);
        }
      } catch (Exception e) {
        LOG.error("can not save activity.", e);
      }
    }
  }
}