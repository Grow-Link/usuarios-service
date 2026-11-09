# user-service

Este es el servicio de usuarios de GrowLink. La carpeta todavia se llama
growlink-app pero de aqui en adelante lo llamamos user-service, para que
combine con trivia-service y cursos-service.

## Por qué microservicios y no un solo proyecto

Al principio pensamos en hacer todo junto, pero el equipo decidio separarlo
en varios servicios. Las razones:

- Cada servicio tiene su propia base de datos y no se meten entre si, asi
  cada quien del equipo puede trabajar en el suyo sin pisarse.

Quedaron 3 servicios:

- **user-service** (este) - usuarios, perfil, y el estado del home
- **cursos-service** - cursos, prerequisitos, roadmap generado con IA
- **trivia-service** - salas de trivia en tiempo real, la parte de concurrencia

## Cómo correrlo

```bash
docker compose up -d   # levanta Postgres en localhost:5433
mvn spring-boot:run    # arranca en localhost:8080
```

Al arrancar se siembran 13 usuarios de prueba (6 USUARIO, 6 PUBLICADOR, 1 ADMIN),
cada uno con nombre y cargo, para poder entrar sin necesidad de registrarse.

Con la app corriendo, `http://localhost:8080/swagger-ui.html` tiene todos los
endpoints documentados y un boton "Authorize" para pegar el token una sola vez.

## Lo que ya tiene

| Metodo | Ruta | Necesita token | Que hace |
|---|---|---|---|
| GET | /api/auth/usuarios | No | Lista los usuarios de prueba para elegir uno |
| POST | /api/auth/login | No | Recibe el usuarioId y devuelve un token |
| GET | /api/perfil/me | Si | Devuelve el perfil actual y si ya esta completo |
| PUT | /api/perfil/metas | Si | Guarda el checkpoint de metas |
| PUT | /api/perfil/intereses | Si | Guarda el checkpoint de intereses |
| PUT | /api/perfil/nivel | Si | Guarda el checkpoint de nivel, aqui se completa el perfil |
| GET | /api/home/estado | Si | Dice si falta perfil, si falta roadmap o si ya hay roadmap, y ademas que secciones puede ver el usuario segun su rol |

El token va firmado aunque no haya password, porque el rol que trae adentro
tiene que ser de fiar. Si alguien pudiera editarlo a mano, se podria hacer
pasar por admin.

### Como sabe /api/home/estado si ya hay roadmap

Le pregunta a cursos-service (`GET /api/roadmap/mio?usuarioId=`), reenviando el
mismo token. La URL se configura con `GROWLINK_CURSOS_SERVICE_URL` (por defecto
`http://localhost:8086`). Si cursos-service no responde o no tiene roadmap
todavia, el estado cae a `CON_PERFIL_SIN_ROADMAP` sin romper el home.

## Sobre HU-02 (los permisos por rol)

La parte que le toca a user-service: /api/home/estado le
dice al que pregunta qué secciones puede ver, segun su rol, y eso lo
decide el backend, no el frontend. Cada rol ve un conjunto distinto:

- USUARIO ve TRIVIA y PERFIL
- PUBLICADOR ve ademas CURSOS y PREGUNTAS_TRIVIA
- ADMIN ve todo lo anterior mas DAR_DE_BAJA_CURSOS y DASHBOARD_METRICAS

Lo que falta de HU-02 es bloquear de verdad los endpoints que hacen esas
cosas (publicar un curso, ver el dashboard), y esos endpoints no existen
en user-service. Van a vivir en cursos-service y trivia-service, y ahi es
donde se termina de construir y probar el resto de la historia.

## Buscar personas y usuario de demostracion

`GET /api/usuarios/buscar?q=` (con token) busca por nombre o cargo (minimo 2 letras, maximo 10 resultados, nunca devuelve a quien busca). Lo usa la trivia para retar a alguien.

El perfil de Esteban (id 5) se completa solo al arrancar, con la misma meta del roadmap de demostracion que siembra cursos-service. Pedir el perfil por primera vez desde varios lugares a la vez no falla: la creacion tolera la carrera (`PerfilCreador`).

## Trivias ganadas (HU-22)

El perfil tiene un contador `triviasGanadas` (sale en `GET /api/perfil/me`).
Lo suma trivia-service cuando alguien gana una partida, llamando a
`POST /api/interno/trivias-ganadas/{usuarioId}`.

- Ese endpoint no es para el frontend. No usa el token de un usuario, se
  protege con la llave interna `GROWLINK_INTERNAL_KEY` en el header
  `X-Internal-Key` (sin llave o con llave mala responde 403).
- La suma la hace la base de datos en un solo UPDATE, no leyendo y escribiendo
  desde Java, asi que si un usuario gana dos partidas a la vez no se pierde
  ninguna (hay una prueba con 10 hilos que lo comprueba).

## Jardín gamificado

Cada roadmap completado (cursos-service) da una **semilla** de la categoría de ese
roadmap; cada trivia ganada da una **gota de agua**. El jardín tiene 6 espacios
(0 a 5): una semilla se planta en un espacio libre y pasa a ser **planta**, que
se riega gastando una gota por riego. Con 3 riegos una planta queda **FLORECIDA**
y ya no se puede regar más (las etapas en orden: `SEMILLA` → `BROTE` → `PLANTA` →
`FLORECIDA`).

| Método | Ruta | Qué hace |
|---|---|---|
| GET | /api/jardin | Gotas, semillas sin plantar y plantas. Antes de responder sincroniza semillas nuevas contra `GET /api/roadmap/completados` de cursos-service |
| POST | /api/jardin/plantas/{id}/plantar | Planta una semilla propia en un espacio (`{"espacio": 0..5}`). 400 fuera de rango, 404 si no es tuya, 409 si ya estaba plantada o el espacio está ocupado |
| POST | /api/jardin/plantas/{id}/regar | Riega una planta propia. 404 si no es tuya, 409 sin gotas o ya florecida |

Todo pasa por el mismo JWT de siempre; cada quien ve y toca solo su propio jardín.

- **Semillas idempotentes**: hay una restricción única `(usuario_id, roadmap_id)`,
  así que sincronizar dos veces (o al mismo tiempo) el mismo roadmap completado
  no duplica la semilla — mismo truco que `PerfilCreador` con el perfil nuevo
  (`SemillaCreador` crea en su propia transacción y tolera el choque).
- **Si cursos-service no responde**, la sincronización no revienta el jardín: se
  loguea un warn y se sigue con las semillas que ya había.
- **Plantar y regar son atómicos** (UPDATE con condición, nunca leer-y-escribir):
  el espacio ocupado lo rechaza la restricción única `(usuario_id, espacio)`, y
  regar con una sola gota entre dos peticiones simultáneas solo le funciona a una
  (hay una prueba con hilos reales que lo comprueba).
- El endpoint interno de trivias ganadas (`POST /api/interno/trivias-ganadas/{id}`)
  ahora también suma 1 gota, en la misma operación atómica que suma la trivia. No
  cambia su contrato ni su respuesta, así que trivia-service no se entera.
- El usuario de demostración (Esteban) arranca con 3 gotas, una semilla sin
  plantar y una planta con 2 riegos, para poder mostrar el jardín sin jugar nada.

## Entrar con Microsoft, roles y tope por IP

El servicio tiene dos formas de saber quien llama, con el interruptor `AUTH_MODE` (el mismo que trivia-service y
cursos-service):

- `legacy` (por defecto): el login quemado de `POST /api/auth/login` y los tokens que firma este servicio. Nada de
  lo que ya funcionaba cambia.
- `oidc`: la identidad la pone Microsoft (Entra ID). Cada peticion trae su JWT y se verifica **solo RS256**, con
  `kid` obligatorio, `exp` obligatorio, y el emisor (`iss`) y la audiencia (`aud`) que digan la configuracion (ver
  multi-tenant, mas abajo). Las llaves publicas se bajan del JWKS del emisor y se cachean (si llega un `kid`
  desconocido se recarga, como maximo una vez por minuto, para que un token con un `kid` inventado no sirva de
  ataque contra el emisor). Lo que no pasa recibe 401, con el motivo en el log y el `X-Request-Id` de la peticion.

Un `AUTH_MODE` mal escrito **tumba el arranque** a proposito: quedarse callado cayendo a `legacy` seria desplegar
con el login viejo creyendo que ya esta puesto el de Microsoft.

### Un directorio o cualquiera (multi-tenant)

La app se registra en Entra ID como **multi-tenant** (cuentas de cualquier directorio organizacional y cuentas
personales de Microsoft). Con eso el `iss` del token trae el tenant de **quien inicia sesion**, no uno fijo, asi
que compararlo con un solo emisor rechazaria a casi todos. El interruptor es `AUTH_MULTI_TENANT`:

- `false` (por defecto): un solo directorio. El emisor es el de `AUTH_ISSUER_URI`, comparado tal cual. **Es el
  comportamiento de siempre, no cambia nada.**
- `true`: se acepta cualquier tenant, pero sin aflojar la validacion. Se exigen tres cosas:
  1. que el `iss` sea exactamente un emisor v2.0 de Entra, con el patron completo y anclado
     (`https://login.microsoftonline.com/<guid-del-tenant>/v2.0`). Esto es lo que rechaza los parecidos, que es el
     ataque de este modo: un `http://` en vez de `https://`, un host como `login.microsoftonline.com.evil.com`, un
     emisor sin `/v2.0`, o `/common` en vez del GUID;
  2. que el claim `tid` sea **el mismo GUID** que aparece en el `iss`, porque el `tid` es el dato con el que se
     decide de que organizacion es la persona;
  3. que ese tenant este en `AUTH_ALLOWED_TENANTS`, si la lista trae algo. Vacia = cualquier tenant que cumpla 1 y
     2. Un GUID mal escrito en la lista **tumba el arranque**, para que no deje fuera a una organizacion entera sin
     que nadie se entere.

Las cuentas personales de Microsoft entran por el mismo camino: su emisor es el del tenant de MSA
(`9188040d-6c67-4c5b-b112-36a304b66dad`), que es un GUID como cualquier otro. Si se pone `AUTH_ALLOWED_TENANTS` y
se quiere que esas cuentas sigan entrando, hay que incluirlo.

`AUTH_AUDIENCE` acepta una **lista separada por comas** y basta con que el `aud` del token coincida con una: segun
como quede configurada la app, Entra pone `api://<client-id>` o el `<client-id>` pelado.

Ejemplo de configuracion para Entra multi-tenant (el `AUTH_JWKS_URI` ya es el valor por defecto en este modo, se
deja escrito para que se vea de donde salen las llaves):

```bash
AUTH_MODE=oidc
AUTH_MULTI_TENANT=true
AUTH_AUDIENCE=api://growlink-usuarios,7f3c1b2a-0000-0000-0000-abcdefabcdef
AUTH_JWKS_URI=https://login.microsoftonline.com/common/discovery/v2.0/keys
# opcional: solo estas organizaciones (vacio = cualquiera)
AUTH_ALLOWED_TENANTS=11111111-2222-3333-4444-555555555555
AUTH_ADMIN_EMAILS=jefa@growlink.co
# con multi-tenant NO se usa AUTH_ISSUER_URI
```

Y para un solo directorio (el modo de antes):

```bash
AUTH_MODE=oidc
AUTH_MULTI_TENANT=false
AUTH_ISSUER_URI=https://login.microsoftonline.com/<tenant>/v2.0
AUTH_AUDIENCE=api://growlink-usuarios
AUTH_JWKS_URI=https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys
```

### El usuario local y su rol

En el primer ingreso se crea el usuario local con el `sub` del token (columna unica `proveedor_sub`), el correo
(claim `email` o, si no viene, `preferred_username`) y el nombre. Arranca como `USUARIO` con `rolElegido=false`. Se
usa el `sub` y no el correo porque el correo se puede cambiar, y el de una cuenta borrada se le puede dar a otra
persona.

- `GET /api/usuarios/me` devuelve `usuarioId` (numerico), `rol`, `rolElegido`, `correo` y `nombre`. Es el endpoint
  con el que cursos-service y trivia-service traducen `sub` -> `usuarioId` + `rol`.
- `PUT /api/usuarios/me/rol` con `{"rol":"PUBLICADOR"}` acepta `USUARIO` o `PUBLICADOR` y marca `rolElegido=true`.
  Se puede cambiar entre esos dos cuando la persona quiera.
- `ADMIN` no se escoge. Si el correo esta en `AUTH_ADMIN_EMAILS` (lista separada por comas, sin distinguir
  mayusculas), el usuario queda `ADMIN` con `rolElegido=true`. Pedir `ADMIN` sin estar en la lista responde 403 y
  deja el intento en el log, y un `ADMIN` no puede bajarse el rol por ese endpoint. La lista se revisa en cada
  ingreso, asi que agregar un correo surte efecto con que la persona vuelva a entrar; quitarlo **no** le baja el
  rol (eso se hace a mano, para no quedarse sin administradores por una variable mal copiada).

### Tope de peticiones por IP

Mas de `LIMITE_IP_MAXIMO_POR_MINUTO` peticiones (300 por defecto) desde la misma IP reciben 429 con `Retry-After`.
La IP real se lee del primer valor de `X-Forwarded-For` (delante hay un balanceador) y si no llega, la del socket.
Cada replica cuenta por su lado, asi que con N replicas el tope real es de hasta N veces el configurado;
`LIMITE_IP_MAXIMO_POR_MINUTO=0` lo apaga.

### Eventos de seguridad

Tres contadores de Micrometer, y una linea de log con el `X-Request-Id` por cada evento (nunca el token: un token
en el log es una credencial en el log):

| Metrica | Que cuenta |
|---|---|
| `growlink.seguridad.tokens-invalidos` | 401: el token falta o no paso la verificacion |
| `growlink.http.limite-ip.rechazos` | 429: peticiones frenadas por el tope por IP (mismo nombre que en cursos-service) |
| `growlink.seguridad.prohibidos` | 403: pidieron `ADMIN` sin estar en la lista, la llave interna no cuadra, o una regla de autorizacion dijo no |

## Despliegue

Se despliega en Azure App Service, el flujo de ramas y ambientes esta
explicado en el README del repo `infra`. `ci.yml` corre las pruebas en cada
push a `main`, `avance` o `final`, y `cd.yml` despliega la rama a su ambiente
de GitHub (`main` -> `actual`, `avance` -> `avance`, `final` -> `final`).
Tambien hay un `Dockerfile`.

El servicio no guarda nada en memoria (la sesion es un JWT, lo demas esta en la
base), asi que se puede correr con varias instancias detras de un balanceador.
Cada respuesta trae el header `X-Instancia` con la instancia que la atendio. Una
cosa: los usuarios de prueba se crean al arrancar si la base esta vacia, por eso
la primera vez hay que dejar una sola instancia hasta que arranque (si no, se
duplican). El script `levantar.sh` del repo `infra` ya lo hace asi.

| Variable | Para que sirve |
|---|---|
| `PORT` y `WEBSITES_PORT` | Poner las dos con `8080` |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | La base Postgres |
| `GROWLINK_JWT_SECRET` | El secreto con el que se firman los tokens, tiene que ser el mismo en los tres servicios |
| `GROWLINK_INTERNAL_KEY` | Llave para las llamadas entre servicios, la misma en trivia-service |
| `GROWLINK_CURSOS_SERVICE_URL` | URL de cursos-service |
| `GROWLINK_CURSOS_CONNECT_TIMEOUT_MS`, `GROWLINK_CURSOS_READ_TIMEOUT_MS` | Opcionales, el plazo maximo para conectar y para esperar la respuesta de cursos-service (por defecto `2000` y `3000` ms) |
| `AUTH_MODE` | `legacy` (por defecto) o `oidc`. Cualquier otro valor tumba el arranque |
| `AUTH_MULTI_TENANT` | `false` (por defecto) = un solo directorio, emisor fijo. `true` = cualquier tenant de Entra, validando el patron del `iss` y que el `tid` cuadre |
| `AUTH_ALLOWED_TENANTS` | Solo con multi-tenant: GUID de tenants permitidos, separados por comas. Vacio = cualquiera. Un GUID mal escrito tumba el arranque |
| `AUTH_ISSUER_URI` | Solo con `oidc` y `AUTH_MULTI_TENANT=false`: quien firma los tokens, p.ej. `https://login.microsoftonline.com/<tenant>/v2.0`. Con multi-tenant no se usa |
| `AUTH_AUDIENCE` | Solo con `oidc`: para que aplicacion tienen que venir. **Lista separada por comas**, basta que coincida una, p.ej. `api://growlink-usuarios,<client-id>` |
| `AUTH_JWKS_URI` | Donde estan las llaves publicas. Vacio con multi-tenant = `https://login.microsoftonline.com/common/discovery/v2.0/keys`; vacio con un solo tenant = `<emisor>/.well-known/jwks.json`, y ahi **con Entra ID hay que ponerla**: `https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys` |
| `AUTH_ADMIN_EMAILS` | Correos con rol `ADMIN`, separados por comas (sin distinguir mayusculas). Vacio = ningun admin |
| `LIMITE_IP_MAXIMO_POR_MINUTO`, `LIMITE_IP_VENTANA_SEGUNDOS` | Opcionales, el tope de peticiones por IP (por defecto `300` por `60` s). `0` lo apaga |

### Salud, trazabilidad y plazos

- **Salud**: `GET /actuator/health` (y `/actuator/health/liveness` y `/readiness`) responde sin credenciales y sin
  detalles. `readiness` incluye la base de datos. No se exponen otros endpoints de actuator.
- **Trazabilidad**: cada peticion lleva `X-Request-Id` (se respeta uno valido, si no se crea), vuelve en la respuesta y
  sale en cada linea del log como `[rid=...]`. La llamada a cursos-service lo reenvia, asi una peticion se sigue por
  los dos servicios.
- **Plazos**: si cursos-service se cuelga, el Home y el jardin siguen sin su respuesta a los pocos segundos (el Home
  muestra "sin roadmap", el jardin una lista vacia) en vez de quedarse esperando.

## Pruebas

```bash
mvn test
```

