# Jarvis-Core

Assistant personnel auto-hébergé en Java 21 / Spring Boot 3.4, conçu pour un Raspberry Pi 4. Le backend utilise SQLite, JDA pour Discord, une interface board Thymeleaf et l'API locale d'Ollama (`gemma2:2b`).

## Structure

```text
src/main/java/com/jarvis/
├── ai/           Client Ollama et génération de texte
├── board/        Entité, dépôt, service et API des tâches
├── common/       Gestion centralisée des erreurs
├── config/       Configuration typée Ollama, Discord et RAG
├── discord/      Bot JDA et commandes textuelles
├── rag/          Import, découpage et interrogation de documents
└── scheduling/   Digest quotidien optionnel des tâches sur Discord
```

Le board et les documents sont conservés dans le volume `/data`. L'index RAG est reconstruit au démarrage à partir des fichiers du dossier persistant.

## Prérequis

- Raspberry Pi OS 64 bits recommandé, Docker Engine et Docker Compose v2.
- Java 21 et Maven 3.9+ pour compiler localement (ou Docker avec Buildx).
- Ollama installé sur le Pi ou sur une machine du réseau, avec le modèle prêt:

```bash
ollama pull gemma2:2b
```

Pour permettre au conteneur Jarvis de joindre Ollama sur le Pi, Ollama doit écouter une interface joignable depuis Docker (par exemple `OLLAMA_HOST=0.0.0.0:11434`). Restreignez le port 11434 au réseau local avec le pare-feu: Ollama n'est pas protégé par une authentification par défaut.

## Compilation et démarrage

Compilation locale:

```bash
mvn clean package
docker compose up -d --build
docker compose logs -f jarvis-core
```

Le conteneur Java est limité à 768 Mo et utilise le ramasse-miettes Serial pour préserver la mémoire du Pi; réservez la majorité des 4 Go à Ollama et au système. L'image de base Temurin est multi-architecture. Le volume nommé `jarvis-data` contient la base SQLite et les documents et survit aux redémarrages.

Pour personnaliser la configuration, créez un fichier `.env` à côté du compose:

```dotenv
SERVER_PORT=8080
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_MODEL=gemma2:2b
DISCORD_ENABLED=true
DISCORD_BOT_TOKEN=remplacer-par-le-token
DISCORD_ALLOWED_USER_IDS=123456789012345678,987654321098765432
DISCORD_CHANNEL_ID=123456789012345678
DISCORD_DIGEST_CHANNEL_ID=123456789012345678
```

Activez l'intent privilégié **Message Content Intent** dans le portail développeur Discord. Le bot refuse de démarrer si le token, le salon dédié (`DISCORD_CHANNEL_ID`) ou la liste blanche d'identifiants sont absents; les commandes des autres salons sont ignorées. `DISCORD_DIGEST_CHANNEL_ID` est facultatif; s'il est renseigné, un digest des tâches ouvertes est publié chaque jour à 08:00 (modifiable avec `TASK_DIGEST_CRON`). En dehors de Discord, l'API HTTP n'inclut pas d'authentification: gardez le port 8080 derrière un pare-feu ou un proxy privé et ne l'exposez pas directement à Internet. Ne commitez jamais le fichier `.env`.

## API et commandes

Board:

- `POST /api/tasks` — JSON: `{"title":"Préparer la réunion","description":"...","priority":"HIGH"}`
- `GET /api/tasks`
- `PATCH /api/tasks/{id}/status` — JSON: `{"status":"IN_PROGRESS"}` (statuts `TODO`, `IN_PROGRESS`, `DONE`; priorités `LOW`, `MEDIUM`, `HIGH`)

Documents:

- `POST /api/documents` — `multipart/form-data`, champ `file`, formats PDF, TXT et Markdown, taille maximale 20 Mo.
- `GET /api/documents`
- `POST /api/documents/query` — JSON: `{"question":"Quels sont les points importants ?"}`
- `POST /api/documents/{nom-stocke}/summary`
- `POST /api/documents/{nom-stocke}/translation?targetLanguage=anglais`

Le répertoire persistant `/data/documents` est également indexé au démarrage. Le RAG MVP découpe les textes en segments avec chevauchement, sélectionne les passages par correspondance lexicale puis fournit le contexte à Ollama; il ne nécessite pas de base vectorielle.

Dans un salon Discord où le bot est présent, les utilisateurs autorisés peuvent utiliser `!help`, `!ask <question>`, `!askdoc <question>`, `!task add <titre>`, `!task list` et `!task done <id>`. Tous les autres utilisateurs sont ignorés.

## Configuration

Les valeurs par défaut et les variables d'environnement correspondantes sont définies dans `src/main/resources/application.yml`. Les variables principales sont `DATABASE_URL`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_TIMEOUT`, `DISCORD_ENABLED`, `DISCORD_BOT_TOKEN`, `DISCORD_ALLOWED_USER_IDS` et `DOCUMENTS_DIRECTORY`.
