-- =====================================================================
--  CEIBA · Autenticación de la consola con correo y contraseña.
--  La contraseña se guarda solo como hash BCrypt. Los usuarios existentes (demo)
--  quedan sin contraseña hasta que un administrador la defina; sin ella no pueden entrar.
-- =====================================================================
ALTER TABLE usuario_consola
  ADD COLUMN clave_hash          varchar(100),
  ADD COLUMN clave_cambiada_en   timestamptz;
