package com.alejandro.synclife.model.entity;

import com.alejandro.synclife.model.enums.Area;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Categoría personalizable dentro de un área (p. ej. "Reuniones" en Trabajo). */
@Entity
@Table(name = "category")
public class Category extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Area area;

    @Column(nullable = false, length = 7)
    private String colorHex;

    private boolean archived;

    private int sortOrder;

    protected Category() {
        // requerido por JPA
    }

    public Category(String name, Area area, String colorHex) {
        this.name = name;
        this.area = area;
        this.colorHex = colorHex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Area getArea() {
        return area;
    }

    public void setArea(Area area) {
        this.area = area;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return name;
    }
}
