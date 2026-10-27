-- Apply the requested fixed +/-50 g tolerance to existing installations.
UPDATE scale_settings SET absolute_grams = 50, percent = 0 WHERE id = 1;
