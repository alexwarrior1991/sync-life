-- Categorías iniciales. El usuario puede renombrarlas, archivarlas o borrarlas.

INSERT INTO category (name, area, color_hex, sort_order, created_at, updated_at) VALUES
    ('Reuniones',         'TRABAJO', '#2563EB', 1, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Proyectos',         'TRABAJO', '#7C3AED', 2, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Correo y gestión',  'TRABAJO', '#0891B2', 3, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Formación',         'TRABAJO', '#4F46E5', 4, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Gimnasio',          'DEPORTE', '#16A34A', 1, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Correr',            'DEPORTE', '#65A30D', 2, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Estiramientos',     'DEPORTE', '#0D9488', 3, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Casa',              'VIDA',    '#D97706', 1, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Recados',           'VIDA',    '#EA580C', 2, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Personal',          'VIDA',    '#DB2777', 3, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')),
    ('Salud',             'VIDA',    '#DC2626', 4, strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'), strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'));
