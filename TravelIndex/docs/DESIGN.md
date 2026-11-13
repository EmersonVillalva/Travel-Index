# Sistema de diseño Travel Index
- **Concepto del icono**: pin de localización con hueco central y una ruta curva con punto de destino. Un solo símbolo de alto contraste, legible a 48 px. Original, sin elementos de otras marcas.
- **Paleta**: Index Navy `#0F1E36`, Route Teal `#1FB5A8`, Sunrise Amber `#F5A524`; fondo `#F4F7FA` / `#0B1220`; superficie `#FFFFFF` / `#131C2E`. Esquemas claro y oscuro en `ui/theme/Theme.kt`.
- **Tipografía prevista**: Plus Jakarta Sans (títulos) e Inter (texto). **Aún no empaquetadas**: la app usa la fuente sans del sistema. Para activarlas, añade los `.ttf` (licencia OFL) a `res/font` y cambia `Brand` en `Theme.kt`.
- **Formas**: esquinas 10/16/24/32 dp. **Espaciado**: 4/8/16/24/32 dp (`Spacing`).
- **Componentes**: `TripCard`, `StatusChip`, `EmptyState`, `SectionTitle`, `EventRow`, `FileCard`, selectores de fecha/hora/zona/moneda y formularios genéricos por módulo (`ModuleSpecs`).
- **Responsive**: `WidthClass` (compacto / medio / ancho). Navegación inferior en móvil y barra lateral en pantallas anchas; el contenido se centra con un ancho máximo y las cuadrículas usan columnas adaptativas. Las barras del sistema y el teclado se gestionan con `WindowInsets`.
- **Accesibilidad**: componentes estándar de Material 3 (objetivos táctiles mínimos incluidos), descripciones de contenido en iconos accionables, estados con icono y texto (no solo color). **No se ha probado todavía con TalkBack ni con tamaños de texto grandes en dispositivo.**
- Los PNG de Play (512×512 y 1024×500) hay que exportarlos desde el vector.
