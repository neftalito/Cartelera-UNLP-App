# UNLP Cartelera Notifier

Aplicación Android que consulta la cartelera de la UNLP (gestiondocente.info.unlp.edu.ar) y envía notificaciones cuando aparecen nuevos avisos, con posibilidad de suscribirse por materia o recibir todas las novedades. La app está desarrollada con Kotlin y Jetpack Compose, utiliza WorkManager para las tareas periódicas y OkHttp/Jsoup para obtener y parsear la información.

## Funcionalidades principales

- Listado y selección de materias disponibles en la cartelera.
- Subscripciones por materia o notificaciones globales.
- Ver detalle de avisos/avisos anulados dentro de la app.
- Sincronización periódica con intervalos configurables.
- Persistencia local para preferencias y cache de materias.

## Tecnologías y stack

- **Kotlin + Jetpack Compose** para la interfaz.
- **WorkManager** para las tareas en segundo plano.
- **OkHttp + Jsoup** para consumir y parsear la cartelera.
- **DataStore** para guardar preferencias del usuario.

## Arquitectura rápida

- `MainActivity.kt` centraliza navegación, permisos de notificaciones y la apertura de detalles desde notificaciones.
- `data/` concentra scraping HTTP/HTML/JSON, normalización de fuentes remotas y persistencia liviana.
- `worker/` ejecuta sincronizaciones periódicas con WorkManager y decide cuándo corresponde notificar cambios.
- `ui/` contiene las pantallas Compose y la lógica de presentación.
- `model/` define las estructuras compartidas entre red, persistencia y UI.

## Flujo de sincronización

1. WorkManager dispara `CarteleraWorker` o `CursadasWorker`.
2. Cada worker usa los servicios de `data/` para descargar y parsear la fuente remota correspondiente.
3. `SettingsStore`, `MateriasStore` y `CursadasStore` guardan snapshots y baselines para detectar cambios y evitar duplicados.
4. `NotificationDispatcher` o `CursadasNotificationDispatcher` construyen la notificación y abren `MainActivity` con el payload necesario.

## Estructura de carpetas

```
.
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── java/com/overcoders/unlpcarteleranotifier
│   │   │   │   ├── data/        # Servicios, repositorios y stores (DataStore/cache)
│   │   │   │   ├── model/       # Modelos de datos (Mensaje, Materia)
│   │   │   │   ├── ui/          # Pantallas Compose y tema
│   │   │   │   └── worker/      # WorkManager y notificaciones
│   │   │   ├── res/             # Recursos Android (strings, themes, icons, etc.)
│   │   │   └── AndroidManifest.xml
│   └── build.gradle.kts         # Configuración del módulo app
├── build.gradle.kts             # Configuración de Gradle a nivel raíz
├── gradle/                      # Wrapper de Gradle
├── gradle.properties            # Propiedades globales
└── settings.gradle.kts          # Definición de módulos
```

## Cómo compilar y ejecutar

1. Abrir el proyecto con **Android Studio**.
2. Esperar a que Gradle sincronice las dependencias.
3. Ejecutar en un dispositivo o emulador con Android 6.0 (API 23) o superior.

### Comandos útiles (opcional)

- Compilar APK debug:
  ```bash
  ./gradlew assembleDebug
  ```
- Ejecutar tests unitarios:
  ```bash
  ./gradlew test
  ```

## Notas adicionales

- La app consulta la cartelera pública de la UNLP y requiere conectividad a internet.
- En Android 13+ se solicita permiso para notificaciones.
