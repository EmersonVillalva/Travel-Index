# Travel Index
**Every trip. One place.**

Hecho por Emerson Villalva Labrador.

Aplicación Android nativa (Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Hilt · MVVM) para organizar viajes. Funciona sin cuenta, sin servidor y **sin internet** (la app ni siquiera tiene ese permiso): todo se guarda en el dispositivo. (El nombre visible es «Travel Index»; el identificador técnico del paquete sigue siendo `com.travelindex.app`.)

> ## Estado real de esta versión (0.6.2)
> - **La 0.6.2 arregla el primer build rojo de la 0.6.x:** `libs.versions.toml` tenía una línea repetida (`androidx-compose-ui-test-manifest`) y Gradle abortaba antes de ejecutar ningún test. Se quita, y el verificador `check_project.py` ahora valida el catálogo (sintaxis, claves repetidas y que cada `libs.…` usado en los `build.gradle.kts` exista) para que no vuelva a llegar a CI.
> - **La base 0.1 y la 0.2 compilaron en GitHub Actions** y la 0.2 se instaló en un móvil. La 0.3.0 llegó a compilar tras un único arreglo (WorkManager), que la 0.3.1 ya no usa. La 0.3.1 a la 0.4.5 no se compilaron en CI; la 0.4.6 y la 0.4.7 sí compilaron y se probaron en un móvil.
> - **La 0.6.0 redondea funciones:** «Escanear documento» usa el escáner de Google (recorte automático, varias páginas en un PDF; sin Play Services cae a la foto de siempre, y la app sigue sin permiso de internet). **Buscador global** (lupa en Inicio y acceso directo del icono): viajes, reservas, lugares, documentos y archivos, sin acentos ni mayúsculas. Cada vuelo avisa ahora **dos veces**: 24 h antes y 1 h antes de salir, así un vuelo guardado a última hora también avisa. Y al mantener pulsado el icono de la app salen los accesos directos «Nuevo viaje» y «Buscar». El borrado conserva su confirmación.
> - **La 0.6.1 añade el widget** «Próximo evento» para la pantalla de inicio: el siguiente plan de tus viajes (o cuándo empieza el próximo), se refresca solo al cambiar los datos y al tocarlo abre la app.
> - **La 0.5.5 blinda el proyecto:** el build de CI tiene tres puertas obligatorias (los 5 verificadores propios, todos los tests y Android Lint): si cualquiera falla, no hay APK y se sube el informe como artefacto. Se estrenan los primeros tests de interfaz (Robolectric + Compose), que pulsan composables reales en CI sin emulador. No se añadió detekt: sin poder ejecutar Gradle aquí, cada dependencia nueva de build es una ruleta en CI; Lint más los verificadores propios cubren lo esencial.
> - **La 0.5.4 es pulido de movimiento:** los desplegables de archivos giran la flecha y animan su contenido; las listas de viajes, reservas y archivos animan altas, bajas y reordenaciones; las páginas del PDF aparecen con un fundido; el visor amplía con doble toque hacia el punto tocado y, ampliado, se arrastra con un dedo (en el PDF en horizontal, para seguir pasando páginas con el dedo); y la app usa el gesto de retroceso predictivo de Android 14.
> - **La 0.5.3 es accesibilidad**, sin ningún cambio visual: los títulos de sección son cabeceras para el lector de pantalla; los desplegables de archivos anuncian su estado («desplegado/plegado») y qué hace el toque; las filas con interruptor se leen como un solo elemento; los errores de los selectores se anuncian, no solo se pintan en rojo; y la columna de la hora del itinerario crece con letra grande en vez de cortar. Falta la prueba real con TalkBack, que solo puede hacerse en un móvil.
> - **La 0.5.2 añade el inglés:** la app completa está en español y en inglés; sigue el idioma del sistema y en Android 13+ se puede elegir por app (Ajustes → Apps → Travel Index → Idioma). El inglés es el idioma base para cualquier otro idioma del mundo. Un verificador propio garantiza que ambos idiomas tienen exactamente los mismos textos y marcadores.
> - **La 0.5.1:** las fechas del viaje se eligen en un solo calendario (marcas ida y vuelta de una vez); los transportes ya no piden localizador de reserva; el apartado Coche queda en un trayecto simple (de dónde a dónde, fecha y hora opcional); y en el itinerario el alojamiento sin hora se coloca con lógica: el día de llegada después del transporte y el último día el check-out antes del transporte de vuelta.
> - **La 0.5.0 añade el visor interno de archivos:** los PDFs y las imágenes se abren dentro de la app (todas las páginas, con zoom de pellizco y doble toque para volver al tamaño normal), con botones de compartir y «Abrir con otra app». Otros formatos, y los PDFs protegidos con contraseña, siguen abriéndose en una app externa. Las vistas previas de las listas quedan como estaban. Sin cambios en la base de datos.
> - **La 0.4.7:** las tarjetas de «Secciones del viaje» tienen todas la misma altura (antes «Visados y documentos de viaje» salía más alta por su título a dos líneas).
> - **La 0.4.6:** el nombre del archivo (xxx.pdf) ya no se ve en ningún sitio: dentro de una reserva los archivos se rotulan por su categoría («Billete», «Reserva»…) y tipo («Documento PDF», «Imagen»), y en «Billetes y reservas» los sueltos también. El nombre real solo aparece al renombrar.
> - **La 0.4.5 es limpieza y un arreglo:** exportar un PDF ya no puede borrar el archivo de «Compartir viaje» pendiente (ni al revés), y se elimina el código muerto de la 0.3.x (pantallas retiradas, viaje de ejemplo y restos del estado de vuelos) con sus textos huérfanos.
> - **La 0.4.4:** el apartado pasa a llamarse «Billetes y reservas»; cada archivo se rotula con el título de su reserva («Free tour Edimburgo») en vez del nombre del archivo, y mientras carga la vista previa del PDF sale un hueco con el icono (sin nombres de archivo sueltos).
> - **La 0.4.3:** abrir un `.travelindex` desde otra app (WhatsApp, correo…) ya no crea una segunda copia de la app: trae la que está abierta con la ventana de importación delante (`launchMode=singleTask`).
> - **La 0.4.2 firma las builds de debug con una clave fija** (`debug.keystore`, clave de pruebas con la contraseña estándar «android»): a partir de ella, el APK de CI se actualiza encima del instalado sin desinstalar. Al pasar de una build anterior a la 0.4.2 hay que desinstalar una última vez (haz antes la copia de seguridad en Perfil y restáurala después). El release de Google Play sigue usando el keystore privado del autor, que no está en el repositorio.
> - **La 0.4.1:** en «Billetes y comprobantes», cada apartado (vuelos, alojamientos, visados…) es un desplegable: se toca y se abren sus archivos. Solo aparecen los apartados que tienen archivos.
> - **La 0.4.0 añade** (con una actualización de la base de datos local a la versión 4): los vuelos muestran fecha, horas, duración y lo pagado en la propia lista; exportar el viaje completo a **PDF**; **historial de viajes** anteriores con resumen; subir archivos **desde el dispositivo o fotografiando el documento**; las actividades piden **duración** (opcional) y la app calcula la hora de fin; los archivos se agrupan por apartado (vuelo, alojamiento, visado…) y la sección pasa a llamarse «Billetes y reservas» (así se nombró en la 0.4.4; en la 0.4.0 fue «Billetes y comprobantes»); y una fila discreta «Apoya el proyecto» en Perfil.
> - **Lo que no se ha podido probar:** la pantalla de cámara, el PDF real y las pantallas nuevas no se han compilado ni ejecutado en Android. Sí se ejecutaron aquí (con `kotlinc` y JUnit) la lógica de duración, agrupación de archivos, historial y maquetación del PDF.
> - **La 0.3.4:** si desmarcas un medio en el viaje, su apartado desaparece aunque tenga reservas (siguen guardadas y vuelven al marcarlo otra vez).
> - **La 0.3.3 hace que los apartados del viaje sigan los medios elegidos:** solo sale «Vuelos» si marcaste avión, y tren, autobús, barco y coche tienen su propio apartado con sus campos (estación, vagón y asiento, puerto, camarote…); con dos medios salen los dos, y sin ninguno elegido salen «Vuelos» y «Transporte» como antes.
> - **La 0.3.2 añade medios de transporte en el viaje (con una actualización de la base de datos local a la versión 3), impide una vuelta anterior a la ida, quita la pantalla «Mapa de vuelos», pone el tema oscuro por defecto y comparte los viajes como archivo `.travelindex`.** Se escribió en un entorno sin Android SDK ni red. Lo que sí se ejecutó aquí:
>   - lógica de dominio (validaciones, itinerario, costes por categoría, nombres de aeropuerto, formato de «compartir viaje», calendario, recordatorios…) compilada con `kotlinc` y **tests JUnit en verde**;
>   - revisiones estáticas: `tools/check_resources.py`, `check_forms.py`, `check_imports.py`, `check_migration.py` y `check_project.py`.
> - Es posible que el primer build muestre algún error de compilación. Pega las líneas `e: file:///...` y se corrige; no es un fallo de diseño.

## Qué incluye
| Área | Contenido |
|---|---|
| Viajes | Crear/editar con portada, fechas (la vuelta nunca puede ser anterior a la ida), **medios de transporte (avión, barco, tren, autobús, coche; se puede elegir más de uno)** y viajeros. Listado con búsqueda, filtros **Todos / Próximos / Pasados / Cancelados**. En los viajes pasados se ve lo que pagaste, separado en vuelos, alojamiento y el resto |
| Dentro de cada viaje | **Agregar**: vuelo, alojamiento, actividad/tour, transporte/coche de alquiler, restaurante, visado o documento de viaje, y **archivo**. Los documentos y archivos solo se alcanzan desde dentro de un viaje |
| Transporte | El viaje tiene los apartados de los medios que elegiste al crearlo: **Vuelos, Trenes, Autobuses, Barcos y Coche**. Cada uno pide lo suyo (el tren no pide número de vuelo). Los trenes, autobuses y barcos usan el mismo almacén de datos que «Transporte»; si no eliges ningún medio salen «Vuelos» y «Transporte» como antes |
| Vuelos | Se escriben a mano: número, fechas, horas, aerolínea, de dónde a dónde (por **nombre** de aeropuerto, con sugerencias) y precio. La **duración se calcula sola** a partir de las horas y las zonas horarias (la llegada es opcional). La lista del apartado ya enseña fecha, horas, duración y lo pagado; cada vuelo muestra una ruta «A → B» y un botón «Abrir en el mapa» |
| Archivos («Billetes y reservas») | Copia privada en el almacenamiento de la app, **agrupada en desplegables por apartado** (vuelo, alojamiento, visado…; solo salen los que tienen archivos). Se añaden **desde el dispositivo o fotografiando el documento** con la cámara del sistema (sin permiso de cámara). **Vista previa visible sin tocar** (imágenes y PDF). Al tocar, el archivo se abre en el **visor interno** (PDF completo e imágenes con zoom; otros formatos van a una app externa). Abrir, compartir, renombrar, asociar a una reserva y borrar |
| Exportar a PDF | El menú del viaje crea un PDF con resumen, itinerario y reservas por apartado, y la lista de archivos. Los números de documentos salen **ocultos** y los archivos no van dentro. Se comparte o se guarda con las apps del dispositivo |
| Historial | Los viajes que ya terminaron, por año, con totales (viajes, días, países, ciudades y lo pagado) |
| Compartir viaje | Se crea un archivo `.travelindex` (propio de la app, no un `.zip` visible) con una **copia exacta** (datos + adjuntos). La otra persona lo toca y se abre en Travel Index (o usa «Importar viaje» en Mis viajes) y el viaje se añade a su móvil. El mensaje incluye el enlace de Google Play para instalar la app (solo funcionará cuando esté publicada) |
| Itinerario y calendario | Derivados de las reservas, con zonas horarias respetadas |
| Notificaciones | Un único interruptor en Perfil: si está activado, avisos locales (vuelo 24 h antes, check-in, actividades, caducidades) |
| Widget y accesos directos | Widget «Próximo evento» en la pantalla de inicio; al mantener pulsado el icono de la app: «Nuevo viaje» y «Buscar» |
| Perfil | Nombre y foto, tema **oscuro (por defecto) o claro**, moneda por defecto, formato de fecha, avisos, bloqueo de capturas, copia de seguridad y restauración, privacidad, acerca de y una fila opcional «Apoya el proyecto» (abre PayPal en el navegador; se puede ocultar con `travelindex.showDonations=false`) |

Se retiraron en la 0.3.0: la consulta de vuelos por internet (en la 0.3.1), pestaña Documentos, presupuesto y gastos, destinos, notas y listas, equipaje, datos de reserva del vuelo (localizador, billete…), tema «sistema», viaje de ejemplo y los selectores de antelación de avisos. El código de esas pantallas retiradas se eliminó del repositorio en la 0.4.5.

## Limitaciones conocidas
- Solo en español.
- **No hay mapa de calles**: dentro de cada vuelo se dibuja su ruta «A → B» y «Abrir en el mapa» abre Google Maps. Ya no existe la pantalla aparte «Mapa de vuelos».
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
