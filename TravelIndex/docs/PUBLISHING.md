# Publicación en Google Play — pendiente de tu parte
1. **Cuenta de desarrollador** de Google Play (tarifa única). Los datos del desarrollador los introduces tú.
2. **Clave de firma**: genera tu keystore (`keytool -genkeypair -v -keystore release-keystore.jks -alias travelindex -keyalg RSA -keysize 2048 -validity 10000`), guárdala en un lugar seguro (y una copia aparte) y rellena `keystore.properties`. Recomendado: activar *Play App Signing*. La clave y las contraseñas nunca van al repositorio ni se comparten en chats.
3. **Paquete definitivo**: confirma `travelindex.applicationId`. No se puede cambiar tras publicar.
4. **Versionado**: sube `travelindex.versionCode` en cada subida (esta versión: 8 / 0.4.0).
5. **Política de privacidad**: URL pública obligatoria. Hay un borrador en `docs/play/PRIVACY_POLICY_es.md`: sustituye los campos entre corchetes y publícala (por ejemplo, GitHub Pages).
6. **Formulario "Seguridad de los datos"** y clasificación de contenido en Play Console. Borrador: la app no recopila ni comparte datos y no tiene permiso de internet; todo queda en el dispositivo.
7. **Ficha**: texto en `docs/play/STORE_LISTING_es.md`; faltan capturas (teléfono y, si procede, tablet), icono 512×512 y gráfico de funciones 1024×500 (hay que exportarlos a PNG).
8. **Permisos declarados**: `POST_NOTIFICATIONS` (avisos) y `RECEIVE_BOOT_COMPLETED` (reprogramar avisos al reiniciar). Sin acceso general a archivos: se usa el selector del sistema. Si añades alarmas exactas o mapas, actualiza la declaración.
9. **Mapas** (si se implementan): clave de Google Maps SDK en Google Cloud Console, restringida por paquete y huella SHA-1; guardada en `local.properties`, nunca en el repositorio.
10. Sube el **AAB** a una pista de **pruebas internas** antes de producción.

## Obtener el APK de prueba y el AAB sin instalar nada (GitHub Actions)
El workflow debe estar en la **raíz del repositorio** como `.github/workflows/build.yml`. Hay una copia de referencia en `.github/workflows/release.yml` de este proyecto.
1. Sube el proyecto a un repositorio **privado** de GitHub (en la subcarpeta `TravelIndex/`).
2. Pestaña **Actions** > "Build TravelIndex" > **Run workflow**. Genera el APK debug (`TravelIndex-debug-apk`) para instalarlo en el móvil.
3. Para el AAB firmado, crea en Settings > Secrets and variables > Actions: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, y ejecuta el workflow marcando "release". Descarga el artefacto `TravelIndex-release` (`.aab` y `.apk`).
4. Play Console > Pruebas > **Pruebas internas** > Crear versión > sube el `.aab`.
Si el workflow falla, copia las últimas líneas del paso en rojo.

## Antes de producción
- Las cuentas personales nuevas suelen exigir una prueba cerrada con un mínimo de testers durante un periodo antes de poder publicar. Comprueba el requisito vigente en Play Console.
- Prueba la app en un móvil real (y a ser posible una tablet o emulador) con el APK debug antes de subir el AAB: crear un viaje, agregar elementos y archivos, calendario, perfil, copia y restauración.

## Enlace de instalación al compartir un viaje
El texto que acompaña a «Compartir viaje» incluye `https://play.google.com/store/apps/details?id=com.travelindex.app` (constante `PLAY_URL` en `data/TripShareManager.kt`). Solo funcionará cuando la app esté publicada con ese paquete; si cambias el paquete, actualiza la constante.
