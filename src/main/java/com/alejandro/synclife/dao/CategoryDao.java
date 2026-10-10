package com.alejandro.synclife.dao;

import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.enums.Area;
import org.hibernate.Session;

import java.util.List;

public class CategoryDao extends AbstractDao<Category> {

    public CategoryDao() {
        super(Category.class);
    }

    public List<Category> findAll(Session session, boolean includeArchived) {
        String hql = "from Category c "
                + (includeArchived ? "" : "where c.archived = false ")
                + "order by c.area, c.sortOrder, c.name";
        return session.createSelectionQuery(hql, Category.class).getResultList();
    }

    public List<Category> findByArea(Session session, Area area) {
        return session.createSelectionQuery(
                        "from Category c where c.area = :area order by c.sortOrder, c.name", Category.class)
                .setParameter("area", area)
                .getResultList();
    }

    public int maxSortOrder(Session session, Area area) {
        Integer max = session.createSelectionQuery(
                        "select max(c.sortOrder) from Category c where c.area = :area", Integer.class)
                .setParameter("area", area)
                .getSingleResult();
        return max == null ? 0 : max;
    }
}
