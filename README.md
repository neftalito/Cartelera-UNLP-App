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
    seguir materias, revisar cursadas y consultar aulas. Con notificaciones
    push cuando aparecen novedades en cartelera o en cursadas.
    <br />
    Hecha con Kotlin, Jetpack Compose y Firebase Cloud Messaging.
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

Consulta datos públicos de `gestiondocente.info.unlp.edu.ar`
y los organiza dentro de la app. Permite seguir la cartelera general,
suscribirse a materias puntuales, revisar cambios de cursada, ver el estado
actual de las aulas, consultar reservas por materia y recibir avisos push con
novedades para todas las materias o aquellas a las que el usuario está suscrito.

## Capturas de pantalla

<p align="center">
  <img src="imagenes/captura1.png" alt="Pantalla principal de la app" width="220" />
  <img src="imagenes/captura2.png" alt="Pantalla de detalle o configuración" width="220" />
  <img src="imagenes/captura3.png" alt="Pantalla adicional de la app" width="220" />
</p>

## Funcionalidades principales

- Feed de cartelera con paginación incremental y filtro por materia.
- Subscripciones por materia o recepción global de novedades.
- Apertura del detalle de anuncios, anuncios anulados y avisos generales.
- Consulta de cursadas por materia con seguimiento de la última actualización.
- Visualización del estado actual de las aulas en la facultad.
- Visualización de reservas de aulas por materia.
- Acciones para compartir o copiar anuncios, cursadas y estado de aulas.
- Sincronización de tópicos de Firebase según las preferencias del usuario.
- Persistencia local para preferencias, materias, cursadas y anuncios vistos.

## Tecnologías

- **Kotlin** para la lógica principal de la aplicación.
- **Jetpack Compose** para la interfaz.
- **Firebase Cloud Messaging** para las notificaciones push y la suscripción a tópicos.
- **OkHttp** y **Jsoup** para consumir y parsear la información remota.
- **DataStore** para guardar preferencias y caché local.
- **WorkManager** solo como compatibilidad transitoria para limpiar tareas legacy.

## Arquitectura rápida

- `MainActivity.kt` centraliza navegación, permisos de notificaciones y apertura de detalles.
- `data/` concentra scraping HTTP/HTML, parsing y persistencia liviana.
- `push/` inicializa Firebase, sincroniza tópicos, recibe data messages y arma notificaciones locales.
- `worker/` conserva compatibilidad transitoria y refrescos locales como el snapshot de cursadas.
- `ui/` contiene las pantallas Compose y la lógica de presentación.
- `model/` define las estructuras compartidas entre red, persistencia y UI.

## Flujo de sincronización

1. La app inicializa Firebase y registra la instalación actual para poder suscribirse a tópicos.
2. `FirebaseTopicSyncManager` mantiene alineados los tópicos reales con `notifyAll` y las subscripciones elegidas por el usuario.
3. Un backend central consulta cartelera y cursadas, detecta cambios y publica data messages en los tópicos correspondientes.
4. `CarteleraFirebaseMessagingService` recibe el push y `PushNotificationDispatcher` arma la notificación local y la apertura dirigida dentro de la app.

## Estructura de carpetas

```text
.
├── app/
│   ├── src/main/
│   │   ├── java/com/overcoders/unlpcarteleranotifier/
│   │   │   ├── data/
│   │   │   ├── model/
│   │   │   ├── push/
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
- `model/`: modelos de datos compartidos entre red, persistencia, push y UI.
- `push/`: integración con Firebase Cloud Messaging, sincronización de tópicos y utilidades de debug para probar pushes reales.
- `ui/`: pantallas de Jetpack Compose, componentes visuales y tema de la aplicación.
- `worker/`: compatibilidad transitoria del esquema anterior y refresco local de snapshots.
- `res/`: recursos Android como colores, textos, iconos, temas y archivos XML de configuración.
- `app/AndroidManifest.xml`: declara la app, permisos, receiver, servicio de Firebase y configuración base de Android.
- `app/build.gradle.kts`: dependencias, versión de la app, SDK objetivo y configuración de compilación del módulo.
- `imagenes/`: logo y capturas usadas por el README.
- `build.gradle.kts`, `settings.gradle.kts` y `gradle.properties`: configuración general del proyecto, módulos y propiedades globales de Gradle.

## Cómo compilar y ejecutar

1. Abrir el proyecto con **Android Studio**.
2. Esperar a que Gradle sincronice las dependencias.
3. Ejecutar en un dispositivo o emulador con Android 6.0 (API 23) o superior.

### Configuración opcional para push real en debug

Si querés probar Firebase real desde una build `debug`, podés crear un archivo
`private-local.properties` en la raíz del repo con estos valores:

```properties
firebase.projectId=...
firebase.applicationId=...
firebase.apiKey=...
firebase.gcmSenderId=...
firebase.serverBaseUrl=...
firebase.serverApiToken=...
```

El archivo de ejemplo está en `private-local.properties.example` y no se versiona.

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
