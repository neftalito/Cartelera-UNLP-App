<a id="readme-top"></a>

<p align="center">
  <a href="https://github.com/neftalito/Cartelera-UNLP-App/graphs/contributors">
    <img src="https://img.shields.io/github/contributors/neftalito/Cartelera-UNLP-App.svg?style=for-the-badge&label=Contribuidores" alt="Contribuidores" />
  </a>
  <a href="https://github.com/neftalito/Cartelera-UNLP-App/network/members">
    <img src="https://img.shields.io/github/forks/neftalito/Cartelera-UNLP-App.svg?style=for-the-badge&label=Bifurcaciones" alt="Bifurcaciones" />
  </a>
  <a href="https://github.com/neftalito/Cartelera-UNLP-App/stargazers">
    <img src="https://img.shields.io/github/stars/neftalito/Cartelera-UNLP-App.svg?style=for-the-badge&label=Estrellas" alt="Estrellas" />
  </a>
  <a href="https://github.com/neftalito/Cartelera-UNLP-App/issues">
    <img src="https://img.shields.io/github/issues/neftalito/Cartelera-UNLP-App.svg?style=for-the-badge&label=Incidencias" alt="Incidencias" />
  </a>
  <a href="https://github.com/neftalito/Cartelera-UNLP-App/blob/main/LICENSE.md">
    <img src="https://img.shields.io/github/license/neftalito/Cartelera-UNLP-App.svg?style=for-the-badge&label=Licencia" alt="Licencia AGPL-3.0" />
  </a>
</p>

<br />
<div align="center">
  <a href="https://github.com/neftalito/Cartelera-UNLP-App">
    <img src="imagenes/logo.png" alt="Logo de Cartelera UNLP App" width="140" height="140">
  </a>

  <h1 align="center">Cartelera UNLP App</h1>

  <p align="center">
    Aplicación Android para consultar la cartelera pública de la UNLP,
    seguir materias y recibir notificaciones cuando aparecen novedades.
    <br />
    Hecha con Kotlin, Jetpack Compose y WorkManager.
    <br />
    <br />
    <a href="https://github.com/neftalito/Cartelera-UNLP-App"><strong>Ver repositorio</strong></a>
    &middot;
    <a href="https://github.com/neftalito/Cartelera-UNLP-App/issues">Reportar bug</a>
    &middot;
    <a href="https://github.com/neftalito/Cartelera-UNLP-App/fork">Hacer fork</a>
  </p>
</div>

## Sobre el proyecto

Cartelera UNLP App consulta la cartelera pública de `gestiondocente.info.unlp.edu.ar`,
detecta publicaciones nuevas y las presenta dentro de la app con notificaciones
configurables. Permite seguir todas las novedades o suscribirse solo a materias
puntuales para reducir ruido.

## Capturas de pantalla

<p align="center">
  <img src="imagenes/captura1.png" alt="Pantalla principal de la app" width="220" />
  <img src="imagenes/captura2.png" alt="Pantalla de detalle o configuración" width="220" />
  <img src="imagenes/captura3.png" alt="Pantalla adicional de la app" width="220" />
</p>

## Funcionalidades principales

- Listado y selección de materias disponibles en la cartelera.
- Subscripciones por materia o notificaciones globales.
- Visualización del detalle de avisos y avisos anulados dentro de la app.
- Visualización del estado actual de las aulas en la facultad.
- Visualización de reservas de aulas por materia.
- Sincronización periódica con intervalos configurables.
- Persistencia local para preferencias, materias y estados de notificación.

## Tecnologías

- **Kotlin** para la lógica principal de la aplicación.
- **Jetpack Compose** para la interfaz.
- **WorkManager** para las tareas periódicas en segundo plano.
- **OkHttp** y **Jsoup** para consumir y parsear la información remota.
- **DataStore** para guardar preferencias y caché local.

## Arquitectura rápida

- `MainActivity.kt` centraliza navegación, permisos de notificaciones y apertura de detalles.
- `data/` concentra scraping HTTP/HTML, parsing y persistencia liviana.
- `worker/` ejecuta sincronizaciones periódicas y decide cuándo notificar cambios.
- `ui/` contiene las pantallas Compose y la lógica de presentación.
- `model/` define las estructuras compartidas entre red, persistencia y UI.

## Flujo de sincronización

1. WorkManager dispara `CarteleraWorker` o `CursadasWorker`.
2. Cada worker usa los servicios de `data/` para descargar y parsear la fuente remota.
3. `SettingsStore`, `MateriasStore` y `CursadasStore` guardan snapshots para detectar cambios y evitar duplicados.
4. `NotificationDispatcher` o `CursadasNotificationDispatcher` construyen la notificación y abren `MainActivity` con el payload necesario.

## Estructura de carpetas

```text
.
├── app/
│   ├── src/main/
│   │   ├── java/com/overcoders/unlpcarteleranotifier/
│   │   │   ├── data/
│   │   │   ├── model/
│   │   │   ├── ui/
│   │   │   └── worker/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
├── imagenes/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── README.md
└── LICENSE.md
```

- `app/`: módulo principal de Android, donde vive prácticamente todo el código de la aplicación.
- `data/`: servicios, scraping, repositorios y almacenamiento local con DataStore para materias, suscripciones, ajustes y cursadas.
- `model/`: modelos de datos compartidos entre red, persistencia, workers y UI.
- `ui/`: pantallas de Jetpack Compose, componentes visuales y tema de la aplicación.
- `worker/`: tareas periódicas con WorkManager, lógica de sincronización y despacho de notificaciones.
- `res/`: recursos Android como colores, textos, iconos, temas y archivos XML de configuración.
- `app/AndroidManifest.xml`: declara la app, permisos, workers, receiver y configuración base de Android.
- `app/build.gradle.kts`: dependencias, versión de la app, SDK objetivo y configuración de compilación del módulo.
- `imagenes/`: logo y capturas usadas por el README.
- `build.gradle.kts`, `settings.gradle.kts` y `gradle.properties`: configuración general del proyecto, módulos y propiedades globales de Gradle.

## Cómo compilar y ejecutar

1. Abrir el proyecto con **Android Studio**.
2. Esperar a que Gradle sincronice las dependencias.
3. Ejecutar en un dispositivo o emulador con Android 6.0 (API 23) o superior.

### Comandos útiles

Compilar APK debug:

```bash
./gradlew assembleDebug
```

Ejecutar tests unitarios:

```bash
./gradlew test
```

En Windows también podés usar:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

## Contribuidores

Las contribuciones son bienvenidas. Si querés proponer cambios, podés abrir un
issue, crear un fork o enviar un pull request.

<a href="https://github.com/neftalito/Cartelera-UNLP-App/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=neftalito/Cartelera-UNLP-App" alt="Contribuidores del proyecto" />
</a>

## Licencia

Distribuido bajo la licencia **GNU Affero General Public License v3.0**.
Ver [LICENSE.md](LICENSE.md) para más información.
