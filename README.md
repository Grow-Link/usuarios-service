# growlink-app

Monolito de GrowLink MVP v2. Reemplaza a los 4 microservicios anteriores
(`identity-service`, `roadmap-service`, `opportunities-service`,
`realtime-service`) — el alcance nuevo (acordado con el profesor: foco en
**concurrencia y tiempo real**, usuarios quemados, dominio de Tecnología)
ya no tiene fronteras de dominio que justifiquen separarlos.

Organizado en capas tipo hexagonal, sin la ceremonia completa de puertos:

```
domain/        entidades y reglas puras (Usuario, Perfil, enums)
application/   casos de uso (AuthService, PerfilService, HomeService)
adapter/
  web/         controladores REST
  persistence/ repositorios Spring Data JPA
  security/    token propio (sin password) + filtro de autenticación
config/        seguridad, seed de datos
```

## Cómo correrlo

```bash
docker compose up -d   # Postgres en localhost:5432
mvn spring-boot:run    # arranca en localhost:8080
```

Al arrancar, `DataSeeder` crea 3 usuarios fixture (uno por rol) si la tabla
está vacía — no hay registro real, es la base de HU-01.

## Cubierto hoy: HU-01, HU-04, HU-05

| Método | Ruta | Auth | Qué hace |
|---|---|---|---|
| `GET` | `/api/auth/usuarios` | No | Lista los usuarios quemados (pantalla de selección). |
| `POST` | `/api/auth/login` | No | `{"usuarioId": 1}` → token firmado con el rol adentro. |
| `GET` | `/api/perfil/me` | Sí | Perfil actual + si ya está completo. |
| `PUT` | `/api/perfil/metas` | Sí | Checkpoint 1. |
| `PUT` | `/api/perfil/intereses` | Sí | Checkpoint 2 — mismo enum que usarán las categorías de curso. |
| `PUT` | `/api/perfil/nivel` | Sí | Checkpoint 3 — al llenarse, `completo` pasa a `true`. |
| `GET` | `/api/home/estado` | Sí | `SIN_PERFIL` \| `CON_PERFIL_SIN_ROADMAP` (`CON_ROADMAP` todavía no es alcanzable — falta HU-11). |

Todo lo que requiere auth va con `Authorization: Bearer <token>`.

**Por qué el token va firmado si "no hay login real":** HU-02 exige que el
backend valide el rol de verdad, no solo esconder botones en el frontend.
Un token sin firmar se podría editar a mano para volverse admin. El token
lleva el rol firmado con la misma librería (`jjwt`) que ya usamos en
`identity-service` — cero curva de aprendizaje nueva, cero contraseña.

## Pruebas

```bash
mvn test
```

`UsuarioQuemadoPerfilHomeTest` cubre las 3 historias de punta a punta contra
H2 real: sin token da 401 (no 403 — ya nos pasó ese bug una vez en
`identity-service`, esta vez lo prevenimos desde el diseño), el camino de
perfil solo se marca completo con los 3 checkpoints llenos, y el estado del
Home cambia en una petición HTTP completamente aparte de la que completó el
perfil (para probar que el estado persiste de verdad, no solo en la
respuesta del PUT).

## Qué sigue (no incluido hoy, a propósito)

- HU-02 (guards de autorización por rol en endpoints protegidos — el token
  ya lleva el rol, falta el `@PreAuthorize`/filtro que lo use)
- HU-06 en adelante: cursos, roadmap con IA, trivia en tiempo real
