package com.alejandro.synclife.service;

import com.alejandro.synclife.AppContext;
import com.alejandro.synclife.TestSupport;
import com.alejandro.synclife.model.entity.Category;
import com.alejandro.synclife.model.entity.Task;
import com.alejandro.synclife.model.enums.Area;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryServiceTest {

    @TempDir
    Path dir;
    AppContext ctx;
    CategoryService categories;

    @BeforeEach
    void setUp() {
        ctx = TestSupport.context(dir);
        categories = ctx.categories();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    private Category byName(String name) {
        return categories.findAll(true).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void groupsSeededCategoriesByArea() {
        Map<Area, List<Category>> grouped = categories.groupedByArea(false);
        assertThat(grouped.get(Area.TRABAJO)).extracting(Category::getName)
                .containsExactly("Reuniones", "Proyectos", "Correo y gestión", "Formación");
        assertThat(grouped.get(Area.DEPORTE)).hasSize(3);
    }

    @Test
    void createsCategoryAtTheEndOfItsArea() {
        Category saved = categories.save(new Category("  Proyecto Alfa ", Area.TRABAJO, "#ff0000"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Proyecto Alfa");
        assertThat(saved.getColorHex()).isEqualTo("#FF0000");
        assertThat(saved.getSortOrder()).isEqualTo(5);
    }

    @Test
    void rejectsDuplicateNameIgnoringCaseAndAccentsWithinTheSameArea() {
        assertThatThrownBy(() -> categories.save(new Category("REUNIONES", Area.TRABAJO, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> categories.save(new Category("formacion", Area.TRABAJO, null)))
                .isInstanceOf(ValidationException.class);
        // Mismo nombre en otra área sí se permite
        assertThat(categories.save(new Category("Reuniones", Area.VIDA, null)).getId()).isNotNull();
    }

    @Test
    void rejectsBlankNameAndInvalidColor() {
        assertThatThrownBy(() -> categories.save(new Category("  ", Area.VIDA, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> categories.save(new Category("Ocio", Area.VIDA, "rojo")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void archivedCategoriesAreHiddenUnlessRequested() {
        Category casa = byName("Casa");
        casa.setArchived(true);
        categories.save(casa);
        assertThat(categories.findAll(false)).extracting(Category::getName).doesNotContain("Casa");
        assertThat(categories.findAll(true)).extracting(Category::getName).contains("Casa");
    }

    @Test
    void deletingACategoryLeavesItsTasksWithoutCategory() {
        Category gym = byName("Gimnasio");
        Task task = new Task("Pierna", Area.DEPORTE);
        task.setCategory(gym);
        long taskId = ctx.tasks().save(task).getId();
        assertThat(categories.countTasks(gym.getId())).isEqualTo(1);
        assertThat(categories.openTaskCounts()).containsEntry(gym.getId(), 1L);

        categories.delete(gym.getId());

        Task reloaded = ctx.tasks().findById(taskId).orElseThrow();
        assertThat(reloaded.getCategory()).isNull();
        assertThat(reloaded.getArea()).isEqualTo(Area.DEPORTE);
    }

    @Test
    void movingACategoryToAnotherAreaMovesItsTasks() {
        Category formacion = byName("Formación");
        Task task = new Task("Curso de inglés", Area.TRABAJO);
        task.setCategory(formacion);
        long taskId = ctx.tasks().save(task).getId();

        formacion.setArea(Area.VIDA);
        categories.save(formacion);

        assertThat(ctx.tasks().findById(taskId).orElseThrow().getArea()).isEqualTo(Area.VIDA);
    }
}
