# SyncLife

Gestor de tareas y rutinas de escritorio, centrado en el **trabajo** y con espacio para el **deporte** y la **vida personal**.
Hecho con **Java 21 + JavaFX 21**, persistencia en **SQLite** mediante **Hibernate 7**, y distribuido como **un único JAR ejecutable** (Windows y Linux).

## Ejecutar

Requisitos: un JDK/JRE **21 o superior** (funciona también con Java 25).

```bash
# Compilar, pasar los tests y generar el JAR ejecutable
./mvnw clean package          # en Windows: mvnw.cmd clean package

# Ejecutar
java -jar target/SyncLife-all.jar
```

En IntelliJ: ejecuta la clase `com.alejandro.synclife.Launcher` (no `SyncLifeApp`).

Los datos se guardan en `~/.synclife/` (en Windows `C:\Users\<usuario>\.synclife\`):
`synclife.db` (SQLite), `settings.properties` y `logs/`. Se puede cambiar con `-Dsynclife.home=/otra/ruta`
o la variable de entorno `SYNCLIFE_HOME`.

> En **Ajustes → Cargar datos de ejemplo** tienes tareas y rutinas de muestra para probarla.

| Hoy | Semana |
|---|---|
| ![Hoy](docs/capturas/hoy.png) | ![Semana](docs/capturas/semana.png) |
| **Rutinas** | **Tema oscuro** |
| ![Rutinas](docs/capturas/rutinas.png) | ![Semana en tema oscuro](docs/capturas/semana-oscuro.png) |

## Pantallas

| Pantalla | Para qué sirve |
|---|---|
| **Hoy** | Agenda del día ordenada por hora, progreso por área, alta rápida, tareas arrastradas de días anteriores y fechas límite próximas. |
| **Semana** | Siete columnas (L–D). Arrastra tareas entre días o desde "Sin programar" para planificar. Filtro por área. |
| **Tareas** | Todas las tareas en una tabla con búsqueda (sin distinguir tildes) y filtros por área, categoría, estado, prioridad y fecha. Selección múltiple (Ctrl/Mayús + clic) para **eliminar varias a la vez** y botón **Borrar completadas**. |
| **Rutinas** | Hábitos recurrentes (p. ej. "Gimnasio L-X-V 19:00"). Cada rutina genera automáticamente la tarea de cada día. |
| **Categorías** | Categorías propias dentro de cada área (Trabajo, Deporte, Vida), con color. Se pueden archivar. |
| **Estadísticas** | Completadas por área y por día, tiempo invertido y cumplimiento de rutinas. |
| **Ajustes** | Tema claro/oscuro, días de antelación de las rutinas, copia de seguridad y carpeta de datos. |

Atajos: **Ctrl+N** nueva tarea · **Ctrl+1…7** cambiar de pantalla · **F5** actualizar · **Supr** eliminar la tarea seleccionada · doble clic para editar · clic derecho para más acciones.

Para **eliminar** una tarea: la papelera al final de cada fila, el botón «Eliminar» del editor o la tecla Supr.
Las tareas que genera una rutina no se eliminan (volverían a aparecer): en su lugar se **omiten** (icono ⏭ o Supr).

## Arquitectura (MVC por capas)

```
src/main/java/com/alejandro/synclife/
├── Launcher.java            punto de entrada del JAR (no extiende Application)
├── SyncLifeApp.java         aplicación JavaFX
├── AppContext.java          raíz de composición: crea Hibernate, DAOs y servicios
├── config/                  rutas de datos, ajustes de usuario, logging
├── persistence/             HibernateUtil, migraciones Flyway, copia de seguridad, converters
├── model/                   MODELO: entidades JPA (Task, Category, Routine) y enums
├── dao/                     consultas HQL (reciben la Session; sin estado)
├── service/                 lógica de negocio y transacciones
├── controller/              CONTROLADORES: uno por vista FXML
└── ui/                      navegación, celdas, controles propios, hilo de BD (FxAsync)
src/main/resources/
├── hibernate.cfg.xml
├── db/migration/            esquema SQL versionado (Flyway)
└── com/alejandro/synclife/  VISTAS: view/*.fxml, css/app.css, images/
```

- **Vista** (FXML + CSS) → **Controlador** (solo llama a servicios) → **Servicio** (transacción) → **DAO** (HQL) → SQLite.
- Todo el acceso a la base de datos se hace en **un único hilo** (`ui/FxAsync`), fuera del hilo de JavaFX: la interfaz no se bloquea y SQLite nunca recibe dos escrituras a la vez.

### Modelo de datos

- **Area** (enum fijo): `TRABAJO`, `DEPORTE`, `VIDA`.
- **Category**: nombre, color y área. Personalizable. Se puede archivar.
- **Task**: título, descripción, área, categoría (opcional), estado, prioridad, día programado, hora, duración estimada, fecha límite y fecha de completado. Si la generó una rutina, apunta a ella.
- **Routine**: nombre, área, categoría, días de la semana (máscara de bits), hora, duración, prioridad, vigencia (desde/hasta) y si está activa.

Las **rutinas generan sus tareas** desde hoy hasta el horizonte configurado (14 días por defecto), nunca para días pasados y sin duplicados.
Una tarea de rutina no se borra ni se cambia de día: se puede **"Omitir"**. Al editar una rutina se regeneran sus tareas pendientes y se conservan las completadas u omitidas.

### Decisiones técnicas

- **Esquema con Flyway, no con `hbm2ddl`**: el dialecto SQLite de Hibernate no genera claves foráneas ni índices únicos compuestos, así que el esquema está en `db/migration/*.sql` y Hibernate solo lo usa (`hbm2ddl.auto=none`).
  Para cambiar el modelo: añade `V3__descripcion.sql` con el `ALTER TABLE` correspondiente y actualiza la entidad.
- **Fechas como texto ISO** (`2026-10-12`, `19:00`) mediante `AttributeConverter`, para que no dependan de la zona horaria.
- **Sin `module-info.java`**: la aplicación se ejecuta en classpath para que el JAR único funcione sin configuración. El aviso `Unsupported JavaFX configuration: classes were loaded from 'unnamed module'` es esperado e inofensivo.
- **JAR multiplataforma**: el `pom.xml` incluye `javafx-graphics` con clasificadores `win` y `linux`. Para añadir macOS, añade también los clasificadores `mac` y `mac-aarch64`.

## Tests

```bash
./mvnw test
```

Incluye tests unitarios (días de la semana, calendario de rutinas, converters, formatos) y de integración contra un SQLite real en un directorio temporal:
migraciones, claves foráneas, almacenamiento de fechas, servicios de tareas, categorías y rutinas.

Prueba de humo de la interfaz completa (recorre todas las pantallas y guarda capturas):

```bash
java -Dsynclife.home=/tmp/synclife-prueba -Dsynclife.smoke=true -Dsynclife.smoke.shots=/tmp/capturas -jar target/SyncLife-all.jar
```

## Créditos

La interfaz usa la fuente [JetBrains Mono](https://www.jetbrains.com/lp/mono/) (la del editor de IntelliJ),
incluida en el JAR bajo la licencia SIL Open Font License 1.1 (`src/main/resources/com/alejandro/synclife/fonts/OFL.txt`).

## Ideas para próximas versiones

- Subtareas / checklist dentro de una tarea.
- Pomodoro y registro de tiempo real frente al estimado en tareas de trabajo.
- Matriz de Eisenhower (urgente / importante) para el trabajo.
- Objetivos a largo plazo por área con tareas vinculadas.
- Revisión semanal guiada.
- Recordatorios y notificaciones.
- Exportar/importar CSV e instalador nativo con `jpackage`.
