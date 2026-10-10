package com.alejandro.synclife.service;

import com.alejandro.synclife.dao.CategoryDao;
import com.alejandro.synclife.dao.TaskDao;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.enums.Area;
import org.hibernate.SessionFactory;
import org.hibernate.exception.ConstraintViolationException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class CategoryService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-F]{6}$");

    private final SessionFactory sessionFactory;
    private final CategoryDao categoryDao;
    private final TaskDao taskDao;

    public CategoryService(SessionFactory sessionFactory, CategoryDao categoryDao, TaskDao taskDao) {
        this.sessionFactory = sessionFactory;
        this.categoryDao = categoryDao;
        this.taskDao = taskDao;
    }

    public List<Category> findAll(boolean includeArchived) {
        return sessionFactory.fromTransaction(s -> categoryDao.findAll(s, includeArchived));
    }

    /** Categorías agrupadas por área; todas las áreas aparecen aunque estén vacías. */
    public Map<Area, List<Category>> groupedByArea(boolean includeArchived) {
        Map<Area, List<Category>> grouped = new EnumMap<>(Area.class);
        for (Area area : Area.values()) {
            grouped.put(area, new ArrayList<>());
        }
        for (Category category : findAll(includeArchived)) {
            grouped.get(category.getArea()).add(category);
        }
        return grouped;
    }

    public Category save(Category category) {
        String name = category.getName() == null ? "" : category.getName().trim();
        if (name.isEmpty()) {
            throw new ValidationException("El nombre de la categoría es obligatorio.");
        }
        if (name.length() > 80) {
            throw new ValidationException("El nombre de la categoría no puede superar 80 caracteres.");
        }
        if (category.getArea() == null) {
            throw new ValidationException("Elige el área de la categoría.");
        }
        String color = category.getColorHex() == null || category.getColorHex().isBlank()
                ? category.getArea().getColorHex()
                : category.getColorHex().trim().toUpperCase();
        if (!HEX_COLOR.matcher(color).matches()) {
            throw new ValidationException("Color no válido: " + color);
        }
        category.setName(name);
        category.setColorHex(color);

        try {
            return sessionFactory.fromTransaction(s -> {
                String normalized = TextSearch.normalize(name);
                boolean duplicate = categoryDao.findByArea(s, category.getArea()).stream()
                        .anyMatch(other -> !Objects.equals(other.getId(), category.getId())
                                && TextSearch.normalize(other.getName()).equals(normalized));
                if (duplicate) {
                    throw new ValidationException(
                            "Ya existe la categoría «" + name + "» en " + category.getArea() + ".");
                }
                if (category.isNew()) {
                    category.setSortOrder(categoryDao.maxSortOrder(s, category.getArea()) + 1);
                    return categoryDao.save(s, category);
                }
                Category current = categoryDao.findById(s, category.getId())
                        .orElseThrow(() -> new ValidationException("La categoría ya no existe."));
                boolean areaChanged = current.getArea() != category.getArea();
                Category saved = categoryDao.save(s, category);
                if (areaChanged) {
                    // Las tareas y rutinas siguen el área de su categoría
                    s.createMutationQuery("update Task t set t.area = :area where t.category.id = :id")
                            .setParameter("area", saved.getArea()).setParameter("id", saved.getId())
                            .executeUpdate();
                    s.createMutationQuery("update Routine r set r.area = :area where r.category.id = :id")
                            .setParameter("area", saved.getArea()).setParameter("id", saved.getId())
                            .executeUpdate();
                }
                return saved;
            });
        } catch (ConstraintViolationException e) {
            throw new ValidationException("Ya existe una categoría con ese nombre en el área.", e);
        }
    }

    /** Borra la categoría; la base de datos deja sus tareas y rutinas sin categoría. */
    public void delete(long id) {
        sessionFactory.inTransaction(s -> categoryDao.delete(s, id));
    }

    public long countTasks(long categoryId) {
        return sessionFactory.fromTransaction(s -> taskDao.countByCategory(s, categoryId));
    }

    public Map<Long, Long> openTaskCounts() {
        return sessionFactory.fromTransaction(taskDao::countOpenByCategory);
    }
}
