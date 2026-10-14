-- delivery-slot-catalog: feriados legales nacionales de Chile 2026 y 2027 (zone_id NULL = todas las zonas).
-- Fuente: calendario de feriados legales publicado en https://www.feriados.cl (2026.php y 2027.php), con su
-- respaldo en las leyes de Biblioteca del Congreso Nacional (https://www.bcn.cl/leychile): Ley 2.977,
-- Ley 19.668 (traslado a lunes de San Pedro y San Pablo y Encuentro de Dos Mundos), Ley 19.973, Ley 20.148,
-- Ley 20.299, Ley 20.629, Ley 20.983 (17 de septiembre cuando el 18 cae sábado) y Ley 21.357 (Pueblos
-- Indígenas en el solsticio de invierno). Consultado el 2026-10-11.
-- Se excluyen feriados regionales (Arica, Chillán). Feriados posteriores (elecciones, leyes especiales) o
-- bloqueos por zona se agregan con una migración nueva.

INSERT INTO catalog_holiday (holiday_date, name) VALUES
    ('2026-01-01', 'Año Nuevo'),
    ('2026-04-03', 'Viernes Santo'),
    ('2026-04-04', 'Sábado Santo'),
    ('2026-05-01', 'Día Nacional del Trabajo'),
    ('2026-05-21', 'Día de las Glorias Navales'),
    ('2026-06-21', 'Día Nacional de los Pueblos Indígenas'),
    ('2026-06-29', 'San Pedro y San Pablo'),
    ('2026-07-16', 'Día de la Virgen del Carmen'),
    ('2026-08-15', 'Asunción de la Virgen'),
    ('2026-09-18', 'Independencia Nacional'),
    ('2026-09-19', 'Día de las Glorias del Ejército'),
    ('2026-10-12', 'Encuentro de Dos Mundos'),
    ('2026-10-31', 'Día de las Iglesias Evangélicas y Protestantes'),
    ('2026-11-01', 'Día de Todos los Santos'),
    ('2026-12-08', 'Inmaculada Concepción'),
    ('2026-12-25', 'Navidad'),
    ('2027-01-01', 'Año Nuevo'),
    ('2027-03-26', 'Viernes Santo'),
    ('2027-03-27', 'Sábado Santo'),
    ('2027-05-01', 'Día Nacional del Trabajo'),
    ('2027-05-21', 'Día de las Glorias Navales'),
    ('2027-06-21', 'Día Nacional de los Pueblos Indígenas'),
    ('2027-06-28', 'San Pedro y San Pablo'),
    ('2027-07-16', 'Día de la Virgen del Carmen'),
    ('2027-08-15', 'Asunción de la Virgen'),
    ('2027-09-17', 'Feriado adicional de Fiestas Patrias'),
    ('2027-09-18', 'Independencia Nacional'),
    ('2027-09-19', 'Día de las Glorias del Ejército'),
    ('2027-10-11', 'Encuentro de Dos Mundos'),
    ('2027-10-31', 'Día de las Iglesias Evangélicas y Protestantes'),
    ('2027-11-01', 'Día de Todos los Santos'),
    ('2027-12-08', 'Inmaculada Concepción'),
    ('2027-12-25', 'Navidad');
