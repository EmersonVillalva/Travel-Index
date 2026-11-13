# Travel Index
**Every trip. One place.**

Hecho por Emerson Villalva Labrador.

Aplicación Android nativa (Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Hilt · MVVM) para organizar viajes. Funciona sin cuenta, sin servidor y **sin internet** (la app ni siquiera tiene ese permiso): todo se guarda en el dispositivo. (El nombre visible es «Travel Index»; el identificador técnico del paquete sigue siendo `com.travelindex.app`.)

> ## Estado real de esta versión (0.3.1)
> - **La base 0.1 y la 0.2 compilaron en GitHub Actions** y la 0.2 se instaló en un móvil. La 0.3.0 llegó a compilar tras un único arreglo (WorkManager), que la 0.3.1 ya no usa.
> - **La 0.3.1 quita la búsqueda de vuelos y el estado del vuelo, y no se ha vuelto a compilar tras quitarlos.** Se escribió en un entorno sin Android SDK ni red. Lo que sí se ejecutó aquí:
>   - lógica de dominio (validaciones, itinerario, costes por categoría, nombres de aeropuerto, formato de «compartir viaje», calendario, recordatorios…) compilada con `kotlinc` y **73 tests JUnit en verde**;
>   - revisiones estáticas: `tools/check_resources.py`, `check_forms.py`, `check_imports.py`, `check_migration.py` y `check_project.py`.
> - Es posible que el primer build muestre algún error de compilación. Pega las líneas `e: file:///...` y se corrige; no es un fallo de diseño.

## Qué incluye
| Área | Contenido |
|---|---|
| Viajes | Crear/editar con portada, fechas y viajeros. Listado con búsqueda, filtros **Todos / Próximos / Pasados / Cancelados**. En los viajes pasados se ve lo que pagaste, separado en vuelos, alojamiento y el resto |
| Dentro de cada viaje | **Agregar**: vuelo, alojamiento, actividad/tour, transporte/coche de alquiler, restaurante, visado o documento de viaje, y **archivo**. Los documentos y archivos solo se alcanzan desde dentro de un viaje |
| Vuelos | Se escriben a mano: número, fechas, horas, aerolínea, de dónde a dónde (por **nombre** de aeropuerto, con sugerencias) y precio. La **duración se calcula sola** a partir de las horas y las zonas horarias (la llegada es opcional). Cada vuelo muestra una ruta «A → B» y un botón «Abrir en el mapa» |
| Archivos | Copia privada en el almacenamiento de la app. **Vista previa visible sin tocar** (imágenes y PDF). Abrir, compartir, renombrar, asociar a una reserva y borrar |
| Compartir viaje | Se crea un archivo `.zip` con una **copia exacta** (datos + adjuntos). La otra persona lo abre con Travel Index (o con «Importar viaje» en Mis viajes) y el viaje se añade a su móvil. El mensaje incluye el enlace de Google Play para instalar la app (solo funcionará cuando esté publicada) |
| Itinerario y calendario | Derivados de las reservas, con zonas horarias respetadas |
| Notificaciones | Un único interruptor en Perfil: si está activado, avisos locales (vuelo 24 h antes, check-in, actividades, caducidades) |
| Perfil | Nombre y foto, tema **claro u oscuro**, moneda por defecto, formato de fecha, avisos, bloqueo de capturas, copia de seguridad y restauración, privacidad, acerca de |

Se retiraron en la 0.3.0: la consulta de vuelos por internet (en la 0.3.1), pestaña Documentos, presupuesto y gastos, destinos, notas y listas, equipaje, datos de reserva del vuelo (localizador, billete…), tema «sistema», viaje de ejemplo y los selectores de antelación de avisos. El código de algunas pantallas retiradas permanece en el repositorio sin usarse (`ui/documents`, `ui/budget`, `ui/notes`, `data/DemoData.kt`) y puede borrarse a mano.

## Limitaciones conocidas
- Solo en español.
- **No hay mapa de calles**: «Abrir en el mapa» dibuja la ruta del vuelo y abre Google Maps.
- No existe un widget en la pantalla de inicio del móvil: el «aviso 24 h antes» es una notificación local.
- **No consulta retrasos, cancelaciones ni busca vuelos por su número**: ningún servicio gratuito y sin clave ofrece esos datos de forma fiable. Los datos del vuelo los escribe el usuario.
- El archivo de «Compartir viaje» y la copia de seguridad **no están cifrados** (pueden incluir números de pasaporte o billete): envíalos solo a personas de confianza.
- La app no puede «pedir instalar» por sí sola a quien no la tiene: el mensaje lleva el enlace de Play (cuando exista) y, una vez instalada, se abre el archivo.
- Tocar una notificación abre la app, pero no navega a la reserva concreta.
- Sin tests de interfaz instrumentados; accesibilidad no probada en dispositivo.

## Compilar
Requisitos: Android Studio Koala o superior (o JDK 17 + Android SDK 35).
1. Abre la carpeta en Android Studio (genera el *Gradle wrapper*), o ejecuta `gradle wrapper --gradle-version 8.9`.
2. Debug: `./gradlew assembleDebug`
3. Tests unitarios: `./gradlew testDebugUnitTest`
4. Release: copia `keystore.properties.example` → `keystore.properties`, rellena **tus** datos y ejecuta `./gradlew assembleRelease` (APK) y `./gradlew bundleRelease` (AAB).

Cambiar el paquete: edita `travelindex.applicationId` en `gradle.properties`. Sube `travelindex.versionCode` en cada subida a Play.

## Revisiones rápidas (sin Android)
```
python3 tools/check_project.py      # XML válido, llaves balanceadas, grafía del nombre «Travel Index»
python3 tools/check_resources.py    # textos usados vs. definidos, argumentos de formato, escapes
python3 tools/check_forms.py        # campos de formulario ↔ códecs
python3 tools/check_imports.py      # imports que faltan o sobran de nombres del proyecto
python3 tools/check_migration.py    # migración Room 1→2 contra los esquemas
```
No sustituyen a compilar con Gradle.

Ver también `docs/PUBLISHING.md`, `docs/DESIGN.md` y `docs/play/`.
