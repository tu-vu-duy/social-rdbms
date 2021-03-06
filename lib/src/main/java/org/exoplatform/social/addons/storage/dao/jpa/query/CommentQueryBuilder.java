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
package org.exoplatform.social.addons.storage.dao.jpa.query;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.exoplatform.commons.persistence.impl.EntityManagerHolder;
import org.exoplatform.social.addons.storage.entity.Activity;
import org.exoplatform.social.addons.storage.entity.Activity_;
import org.exoplatform.social.addons.storage.entity.Comment;
import org.exoplatform.social.addons.storage.entity.Comment_;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * Jun 2, 2015  
 */
public final class CommentQueryBuilder {

  private Long activityId;
  private Long commentId;
  //newer or older
  private long sinceTime = 0;
  private boolean isNewer = false;
  private long offset;
  private long limit = 0;
  
  //order by
  private boolean descOrder = false;
  
  public static CommentQueryBuilder builder() {
    return new CommentQueryBuilder();
  }
  
  public CommentQueryBuilder activityId(Long activityId) {
    this.activityId = activityId;
    return this;
  }

  public CommentQueryBuilder commentId(Long commentId) {
    this.commentId = commentId;
    return this;
  }
  
  public CommentQueryBuilder newer(long sinceTime) {
    this.isNewer = true;
    this.sinceTime = sinceTime;
    return this;
  }
  
  public CommentQueryBuilder older(long sinceTime) {
    this.isNewer = false;
    this.sinceTime = sinceTime;
    return this;
  }
  
  public CommentQueryBuilder offset(long offset) {
    this.offset = offset;
    return this;
  }
  
  public CommentQueryBuilder limit(long limit) {
    this.limit = limit;
    return this;
  }
  
  public CommentQueryBuilder ascOrder() {
    this.descOrder = false;
    return this;
  }
  
  public CommentQueryBuilder descOrder() {
    this.descOrder = true;
    return this;
  }
  
  /**
   * Builds the Typed Query
   * @return
   */
  public TypedQuery<Comment> build() {
    EntityManager em = EntityManagerHolder.get();
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Comment> criteria = cb.createQuery(Comment.class);
    Root<Comment> comment = criteria.from(Comment.class);
    Join<Comment, Activity> activity = comment.join(Comment_.activity);
    
    List<Predicate> predicates = new ArrayList<Predicate>();
    //owner
    if (this.activityId != null) {
      predicates.add(cb.equal(activity.get(Activity_.id), this.activityId));
    }
    
    //newer or older
    if (this.sinceTime > 0) {
      if (isNewer) {
        predicates.add(cb.greaterThan(comment.<Long>get(Comment_.lastUpdated), this.sinceTime));
      } else {
        predicates.add(cb.lessThan(comment.<Long>get(Comment_.lastUpdated), this.sinceTime));
      }
    }
    
    //filter hidden = FALSE
    predicates.add(cb.equal(comment.<Boolean>get(Comment_.hidden), Boolean.FALSE));
    
    CriteriaQuery<Comment> select = criteria.select(comment);
    select.where(predicates.toArray(new Predicate[0]));
    if (this.descOrder) {
      select.orderBy(cb.desc(comment.<Long> get(Activity_.lastUpdated)));
    } else {
      select.orderBy(cb.asc(comment.<Long> get(Activity_.lastUpdated)));
    }

    TypedQuery<Comment> typedQuery = em.createQuery(select);
    if (this.limit > 0) {
      typedQuery.setFirstResult((int) offset);
      typedQuery.setMaxResults((int) limit);
    }
    
    return typedQuery;
  }
  
  /**
   * Build count statement to get the number of the comment base on given conditions
   * 
   * @return TypedQuery<Long> instance 
   */
  public TypedQuery<Long> buildCount() {
    EntityManager em = EntityManagerHolder.get();
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Long> criteria = cb.createQuery(Long.class);
    Root<Comment> comment = criteria.from(Comment.class);
    Join<Comment, Activity> activity = comment.join(Comment_.activity);
    
    List<Predicate> predicates = new ArrayList<Predicate>();
    //owner
    if (this.activityId != null) {
      predicates.add(cb.equal(activity.get(Activity_.id), this.activityId));
    }
    
    //newer or older
    if (this.sinceTime > 0) {
      if (isNewer) {
        predicates.add(cb.greaterThan(comment.<Long>get(Comment_.lastUpdated), this.sinceTime));
      } else {
        predicates.add(cb.lessThan(comment.<Long>get(Comment_.lastUpdated), this.sinceTime));
      }
    }
    
    //hidden
    predicates.add(cb.equal(activity.<Boolean>get(Comment_.hidden), Boolean.FALSE));
    
    CriteriaQuery<Long> select = criteria.select(cb.count(comment));
    select.where(predicates.toArray(new Predicate[0]));

    return em.createQuery(select);
  }
  
  public Activity buildActivty() {
    EntityManager em = EntityManagerHolder.get();
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Activity> criteria = cb.createQuery(Activity.class);
    Root<Activity> a = criteria.from(Activity.class);
    ListJoin<Activity, Comment> o = a.join(Activity_.comments, JoinType.LEFT);
    Predicate p = cb.equal(o.get(Comment_.id), commentId);

    CriteriaQuery<Activity> select = criteria.select(a);
    select.where(p);
    TypedQuery<Activity> typedQuery = em.createQuery(select);
    return typedQuery.getSingleResult();
  }
}
