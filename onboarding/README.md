# Onboarding (app móvil)

App de apertura de cuenta hecha en Flutter, para Android e iOS.

## 1. Instalar las herramientas (solo la primera vez)

1. Instalar [Android Studio](https://developer.android.com/studio).
2. Instalar Flutter:

   **Mac**
   ```bash
   brew install --cask flutter
   ```

   **Windows:** seguir la [guía oficial](https://docs.flutter.dev/get-started/install/windows) y agregar Flutter al `PATH`.

3. En Android Studio: **Settings → Languages & Frameworks → Android SDK → SDK Tools** → marcar **Android SDK Command-line Tools (latest)** → **Apply**.
4. Aceptar las licencias de Android (responder `y` a todo):
   ```bash
   flutter doctor --android-licenses
   ```
5. Verificar la instalación. **Flutter** y **Android toolchain** deben salir con ✓; los avisos de Xcode o Visual Studio se pueden ignorar.
   ```bash
   flutter doctor
   ```

## 2. Preparar dónde correr la app

**Emulador:** Android Studio → **Device Manager** → **+** → **Medium Phone** → imagen **arm64** (Mac con chip M) o **x86_64** (Windows/Intel) → en *Additional settings*: **RAM 2 GB**, **2 CPU cores** → **Finish**.

Ver y encender los emuladores desde la terminal:
```bash
flutter emulators
```
```bash
flutter emulators --launch Medium_Phone_API_37.0
```

**Celular Android con cable:** activar **Opciones de desarrollador** (tocar 7 veces *Número de compilación* en *Acerca del teléfono*) → activar **Depuración USB** → conectar el cable y aceptar el permiso. Para confirmar que se detecta:
```bash
flutter devices
```

## 3. Descargar el proyecto (solo la primera vez)

```bash
git clone https://github.com/dream-parking/verificacion-online.git
```
```bash
cd verificacion-online/onboarding
```
```bash
flutter pub get
```

## 4. Correr la app

Con el emulador encendido o el celular conectado, dentro de `onboarding/`:
```bash
flutter run
```
La primera vez tarda varios minutos. Con la app abierta, en la terminal:

| Tecla | Acción |
|---|---|
| `r` | Recargar cambios (hot reload) |
| `R` | Reiniciar la app |
| `q` | Salir |

### ¿A qué API se conecta?

Por defecto la app usa la API de **Dev** (`https://api.dev.identidad.alambritos.online`), así que no hace falta levantar el backend. La primera petición puede tardar hasta un minuto si el servidor estaba dormido.

Para usar la API de **QA**:
```bash
flutter run --dart-define=APP_ENV=qa
```

Para usar el backend corriendo en tu Mac (desde el emulador de Android, `10.0.2.2` es tu Mac):
```bash
flutter run --dart-define=API_URL=http://10.0.2.2:8080
```

## 5. Ver los cambios nuevos del equipo

Después de que se fusione un PR a `main`:
```bash
git switch main
```
```bash
git pull
```
```bash
flutter pub get
```
```bash
flutter run
```

Para probar un PR antes de que se fusione:
```bash
git fetch
```
```bash
git switch nombre-de-la-rama
```
```bash
flutter run
```

## 6. Correr las pruebas

```bash
flutter analyze
```
```bash
flutter test
```

## Problemas comunes

- **"No supported devices connected":** el emulador no está encendido o el celular no tiene la depuración USB activada.
- **Sugiere correr `flutter create .`:** no hacerlo; agrega plataformas que el proyecto no usa.
- **Error de licencias de Android:** volver a correr `flutter doctor --android-licenses`.
- **Si algo falla después de un `git pull`:**
  ```bash
  flutter clean
  ```
  ```bash
  flutter pub get
  ```
- **iPhone:** se necesita una Mac con Xcode instalado; mientras tanto se puede usar el emulador de Android.
