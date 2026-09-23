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
docker compose up -d   # levanta Postgres en localhost:5432
mvn spring-boot:run    # arranca en localhost:8080
```

Al arrancar se crean 3 usuarios de prueba, uno por rol, para poder entrar
sin necesidad de registrarse.

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

## Pruebas

```bash
mvn test
```

