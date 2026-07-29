# Cómo contribuir a Cartelera UNLP App

Gracias por dedicar tiempo a mejorar Cartelera UNLP App. Se aceptan reportes de
errores, propuestas de funcionalidades, mejoras de documentación, pruebas y
cambios de código.

Esta guía explica el flujo recomendado para que una contribución pueda
revisarse e integrarse con la menor fricción posible.

## Antes de empezar

- Revisá los [issues abiertos](https://github.com/neftalito/Cartelera-UNLP-App/issues)
  y cerrados para evitar duplicados.
- Para informar un error, usá el
  [formulario de bug](https://github.com/neftalito/Cartelera-UNLP-App/issues/new?template=bug_report.yml).
- Para proponer una mejora, usá el
  [formulario de nueva funcionalidad](https://github.com/neftalito/Cartelera-UNLP-App/issues/new?template=feature_request.yml).
- Si el cambio es grande, modifica la navegación, incorpora una fuente de datos
  o afecta las notificaciones, esperá a que se acuerden el alcance y el enfoque
  en el issue antes de implementarlo.
- Las correcciones pequeñas, la documentación y las pruebas aisladas pueden
  enviarse directamente en un pull request si su objetivo está bien explicado.

No publiques claves, tokens, credenciales, datos personales ni información
privada en issues, capturas, logs, commits o pull requests.

## Preparar el entorno

### Requisitos

- Git.
- Android Studio con un Android SDK compatible con el proyecto.
- JDK 21 para ejecutar Gradle, igual que en la integración continua.
- Un emulador o dispositivo Android únicamente si vas a ejecutar las pruebas
  instrumentadas.

El proyecto usa su propio Gradle Wrapper, por lo que no hace falta instalar
Gradle globalmente.

### Crear un fork y clonar

1. Creá un fork del repositorio en GitHub.
2. Cloná tu fork y registrá el repositorio original como `upstream`:

   ```bash
   git clone https://github.com/TU-USUARIO/Cartelera-UNLP-App.git
   cd Cartelera-UNLP-App
   git remote add upstream https://github.com/neftalito/Cartelera-UNLP-App.git
   ```

3. Sincronizá `develop` antes de crear una rama:

   ```bash
   git fetch upstream
   git switch develop
   git pull --ff-only upstream develop
   ```

4. Creá una rama con un nombre breve y descriptivo:

   ```bash
   git switch -c feature/nombre-de-la-funcionalidad
   ```

Podés usar prefijos como `feature/`, `fix/`, `docs/`, `refactor/` o `test/`
según el tipo de cambio.

### Primera verificación

Las builds `debug` funcionan con la configuración Firebase de ejemplo incluida.
Para comprobar que el entorno está listo:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

En Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Android Studio genera `local.properties` con la ruta del SDK. Ese archivo, los
archivos `private-local.properties` reales y cualquier credencial deben
permanecer fuera de Git.

## Cómo añadir una funcionalidad

### 1. Definir el comportamiento

Antes de escribir código, dejá claro:

- qué problema resuelve;
- quién se beneficia;
- qué queda dentro y fuera del alcance;
- cómo se comprobará que está terminada;
- qué sucede cuando no hay red, la respuesta está vacía o la fuente falla.

Una funcionalidad visible debería tener criterios de aceptación concretos en su
issue.

### 2. Ubicar cada responsabilidad

Mantené la organización existente:

- `model/`: modelos compartidos entre red, persistencia, notificaciones y UI;
- `data/`: acceso HTTP, parsing, repositorios, DataStore y cachés;
- `ui/`: pantallas de entrada de Compose;
- `ui/<feature>/`: estado, contenido, acciones y componentes de una
  funcionalidad que necesita más de un archivo;
- `ui/common/`: componentes reutilizables por varias pantallas;
- `navigation/`: rutas, tabs, diálogos y estructura global de navegación;
- `push/`: inicialización de Firebase, tópicos, payloads y notificaciones.

Evitá concentrar red, parsing, estado y presentación en un único composable. Si
una pantalla crece, separá su estado y su contenido siguiendo las features
existentes.

### 3. Integrar datos remotos de forma segura

- Reutilizá `AppHttpClient` para las solicitudes HTTP.
- No bloquees el hilo principal.
- Conservá la cancelación de corrutinas; una `CancellationException` no debe
  convertirse en un error visible ni tratarse como un fallo común.
- Definí timeouts y mensajes de error útiles para la persona usuaria.
- Si la función admite datos en caché, diferenciá claramente contenido
  actualizado, contenido guardado y error sin datos disponibles.
- No agregues endpoints internos, credenciales ni valores de producción al
  código.

Cuando se parsee HTML de una web externa:

- usá selectores basados en una estructura estable;
- manejá elementos ausentes y páginas vacías;
- añadí un fixture mínimo y anonimizado en
  `app/src/test/resources/fixtures/`;
- probá el parser contra el fixture, sin depender únicamente de la web en vivo;
- documentá en el issue o pull request la URL pública que actúa como fuente.

### 4. Diseñar la interfaz

- Conservá el estilo actual de Material 3 y Jetpack Compose.
- Mantené los textos visibles en español y con redacción orientada a la persona
  usuaria.
- Incluí estados de carga, vacío, error y reintento cuando correspondan.
- Cuidá accesibilidad, contraste, áreas táctiles, descripciones de contenido y
  tamaños de texto.
- Evitá cambios de diseño globales dentro de una funcionalidad no relacionada.

Si la funcionalidad agrega una pantalla, conectala desde `navigation/` y
verificá el comportamiento de Atrás, los cambios de configuración y la
restauración del estado relevante.

### 5. Cubrir el comportamiento con pruebas

Colocá las pruebas junto al tipo de comportamiento que validan:

- `app/src/test/`: lógica, estado, filtros, parsers, repositorios, cachés y
  transformación de datos;
- `app/src/test/resources/fixtures/`: respuestas HTML o JSON pequeñas y
  deterministas;
- `app/src/androidTest/`: integración con Android o Compose que no pueda
  comprobarse como prueba unitaria.

Como mínimo, probá el camino principal y los casos límite introducidos por el
cambio. Cuando aplique, incluí respuestas vacías o inválidas, fallos de red,
contenido en caché, reintentos y cancelación.

### 6. Actualizar la documentación

Actualizá `README.md` si el cambio modifica:

- las funcionalidades visibles;
- las fuentes de datos;
- la estructura relevante del repositorio;
- los requisitos de compilación o configuración;
- el flujo de notificaciones o sincronización.

Los comentarios de código deben explicar decisiones o restricciones no obvias,
no repetir literalmente lo que ya expresa el código.

## Estilo y alcance de los cambios

- Seguí el estilo Kotlin ya presente y usá cuatro espacios de indentación.
- Preferí funciones pequeñas, nombres descriptivos y estado inmutable cuando sea
  razonable.
- No mezcles una funcionalidad con refactors o actualizaciones de dependencias
  que no sean necesarios para implementarla.
- No cambies `versionCode`, `versionName`, firma, publicación o workflows salvo
  que el issue requiera expresamente ese trabajo.
- No incluyas archivos generados, APK, AAB, keystores, configuraciones del IDE ni
  secretos.
- Mantené cada commit enfocado. Los mensajes actuales del proyecto son breves,
  descriptivos y suelen usar verbos en primera persona, por ejemplo:
  `Agrego pruebas para el parser de calendario` o
  `Corrijo la restauración del filtro de materias`.

## Verificar el cambio

Antes de abrir un pull request, ejecutá al menos:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

En Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

Para reproducir las comprobaciones de build de CI también se ejecutan
`:app:assembleRelease` y `:app:assembleDebugAndroidTest`. La variante `release`
necesita valores Firebase con formato válido. Podés crear un
`private-local.properties` exclusivamente local con los valores no productivos
que usa CI:

```properties
firebase.projectId=ci-cartelera-checks
firebase.applicationId=1:123456789012:android:0123456789abcdef
firebase.apiKey=AIza00000000000000000000000000000000000
firebase.gcmSenderId=123456789012
```

Luego ejecutá:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-daemon
```

Las pruebas instrumentadas requieren un emulador o dispositivo:

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Si no podés ejecutar alguna comprobación, indicalo expresamente en el pull
request junto con el motivo. No hace falta disponer de credenciales de
producción ni de firma para contribuir.

## Abrir un pull request

Los pull requests normales deben apuntar a `develop`, salvo que una persona
mantenedora indique otra rama. `main` representa la línea estable.

Antes de enviarlo:

- actualizá tu rama con el estado reciente de `upstream/develop`;
- revisá el diff completo y eliminá cambios accidentales;
- confirmá que no se incorporaron secretos o datos privados;
- vinculá el issue con `Closes #123` cuando corresponda;
- explicá el problema, el enfoque elegido y cualquier decisión importante;
- enumerá las verificaciones ejecutadas;
- incluí capturas o un video breve para cambios visuales;
- indicá qué quedó sin verificar o fuera de alcance.

Mantené el pull request acotado. Si durante la implementación aparece un
problema independiente, abrí otro issue o pull request.

La integración continua verificará tests unitarios, lint y builds. Los pull
requests a `develop` y `main` también ejecutan pruebas instrumentadas. Un fallo
de CI debe resolverse o explicarse antes de solicitar la revisión final.

## Revisión

Durante la revisión pueden solicitarse cambios de comportamiento, pruebas,
documentación o simplificación. Respondé dentro de los hilos correspondientes y
marcá qué modificaste. Evitá reescribir el historial después de que comenzó la
revisión salvo que se coordine previamente.

La aceptación de un issue no garantiza que una implementación concreta se
integre: también se evalúan mantenimiento, seguridad, experiencia de usuario y
coherencia con el alcance del proyecto.

## Licencia

Al enviar una contribución, aceptás que se distribuya bajo la misma
[GNU Affero General Public License v3.0](LICENSE.md) que el resto del proyecto.
