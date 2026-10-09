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

## Pruebas

```bash
mvn test
```

