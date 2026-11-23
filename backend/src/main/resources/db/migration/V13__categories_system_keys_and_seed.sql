ALTER TABLE categories
    ADD COLUMN system_key VARCHAR(40);

CREATE UNIQUE INDEX uq_categories_system_key
    ON categories (system_key)
    WHERE system_key IS NOT NULL;

-- Top-level system EXPENSE categories (18)
INSERT INTO categories (family_id, parent_category_id, name, kind, icon, is_system, is_active, system_key)
VALUES
    (NULL, NULL, 'Supermercado',        'EXPENSE', 'shopping-cart',   TRUE, TRUE, 'SUPERMARKET'),
    (NULL, NULL, 'Alquiler y expensas', 'EXPENSE', 'house',           TRUE, TRUE, 'HOUSING'),
    (NULL, NULL, 'Servicios',           'EXPENSE', 'plug-zap',        TRUE, TRUE, 'UTILITIES'),
    (NULL, NULL, 'Prepaga y salud',     'EXPENSE', 'heart-pulse',     TRUE, TRUE, 'HEALTH'),
    (NULL, NULL, 'Educación',           'EXPENSE', 'graduation-cap',  TRUE, TRUE, 'EDUCATION'),
    (NULL, NULL, 'Vehículos',           'EXPENSE', 'car',             TRUE, TRUE, 'VEHICLES'),
    (NULL, NULL, 'Salidas',             'EXPENSE', 'party-popper',    TRUE, TRUE, 'OUTINGS'),
    (NULL, NULL, 'Transporte',          'EXPENSE', 'bus',             TRUE, TRUE, 'TRANSPORT'),
    (NULL, NULL, 'Indumentaria',        'EXPENSE', 'shirt',           TRUE, TRUE, 'CLOTHING'),
    (NULL, NULL, 'Hogar',               'EXPENSE', 'sofa',            TRUE, TRUE, 'HOME'),
    (NULL, NULL, 'Mascotas',            'EXPENSE', 'paw-print',       TRUE, TRUE, 'PETS'),
    (NULL, NULL, 'Hijos',               'EXPENSE', 'baby',            TRUE, TRUE, 'KIDS'),
    (NULL, NULL, 'Tecnología',          'EXPENSE', 'laptop',          TRUE, TRUE, 'TECH'),
    (NULL, NULL, 'Suscripciones',       'EXPENSE', 'repeat',          TRUE, TRUE, 'SUBSCRIPTIONS'),
    (NULL, NULL, 'Regalos',             'EXPENSE', 'gift',            TRUE, TRUE, 'GIFTS'),
    (NULL, NULL, 'Impuestos',           'EXPENSE', 'landmark',        TRUE, TRUE, 'TAXES'),
    (NULL, NULL, 'Costos financieros',  'EXPENSE', 'percent',         TRUE, TRUE, 'FINANCIAL_COSTS'),
    (NULL, NULL, 'Otros gastos',        'EXPENSE', 'circle-ellipsis', TRUE, TRUE, 'OTHER_EXPENSE');

-- Child system EXPENSE categories under VEHICLES (6)
INSERT INTO categories (family_id, parent_category_id, name, kind, icon, is_system, is_active, system_key)
VALUES
    (NULL, (SELECT id FROM categories WHERE system_key = 'VEHICLES'), 'Combustible',              'EXPENSE', 'fuel',      TRUE, TRUE, 'VEHICLES_FUEL'),
    (NULL, (SELECT id FROM categories WHERE system_key = 'VEHICLES'), 'Mantenimiento',            'EXPENSE', 'wrench',    TRUE, TRUE, 'VEHICLES_MAINTENANCE'),
    (NULL, (SELECT id FROM categories WHERE system_key = 'VEHICLES'), 'Seguro',                   'EXPENSE', 'shield',    TRUE, TRUE, 'VEHICLES_INSURANCE'),
    (NULL, (SELECT id FROM categories WHERE system_key = 'VEHICLES'), 'Patente',                  'EXPENSE', 'file-text', TRUE, TRUE, 'VEHICLES_TAXES'),
    (NULL, (SELECT id FROM categories WHERE system_key = 'VEHICLES'), 'Peajes y estacionamiento', 'EXPENSE', 'ticket',    TRUE, TRUE, 'VEHICLES_TOLLS'),
    (NULL, (SELECT id FROM categories WHERE system_key = 'VEHICLES'), 'Multas',                   'EXPENSE', 'siren',     TRUE, TRUE, 'VEHICLES_FINES');

-- Top-level system INCOME categories (7)
INSERT INTO categories (family_id, parent_category_id, name, kind, icon, is_system, is_active, system_key)
VALUES
    (NULL, NULL, 'Sueldo',             'INCOME', 'briefcase',       TRUE, TRUE, 'SALARY'),
    (NULL, NULL, 'Aguinaldo',          'INCOME', 'sparkles',        TRUE, TRUE, 'BONUS'),
    (NULL, NULL, 'Trabajos freelance', 'INCOME', 'laptop',          TRUE, TRUE, 'FREELANCE'),
    (NULL, NULL, 'Alquileres',         'INCOME', 'key-round',       TRUE, TRUE, 'RENT_INCOME'),
    (NULL, NULL, 'Rendimientos',       'INCOME', 'trending-up',     TRUE, TRUE, 'INVESTMENTS'),
    (NULL, NULL, 'Mensualidad',        'INCOME', 'piggy-bank',      TRUE, TRUE, 'ALLOWANCE_INCOME'),
    (NULL, NULL, 'Otros ingresos',     'INCOME', 'circle-ellipsis', TRUE, TRUE, 'OTHER_INCOME');
