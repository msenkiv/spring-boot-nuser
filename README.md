# Nuser

API REST para cadastro, consulta e atualização de usuários, construída com Java 21,
Spring Boot, Spring MVC, Spring Security, Spring Data JPA e PostgreSQL 17.

## Executar com Docker Compose

Requisito: Docker com Docker Compose disponível.

1. Copie o modelo de configuração, caso ainda não tenha um `.env`:

   ```sh
   cp .env.example .env
   ```

2. Defina `DB_PASSWORD` e uma `API_KEY` longa e aleatória no `.env`.
3. Construa e inicie os serviços:

   ```sh
   docker compose up --build -d
   docker compose logs -f app
   ```

A aplicação estará em `http://localhost:8080` e o banco em `localhost:5432`.
Se alguma porta já estiver ocupada, ajuste `APP_PORT` ou `DB_PORT` no `.env`.
Entre containers, a aplicação usa `db:5432`, independentemente da porta publicada
no computador. Os serviços são publicados apenas na interface local por padrão.


## Executar pelo IntelliJ ou Maven

Requisitos: JDK 21 e um PostgreSQL com o schema de `database/init.sql` aplicado.
Também é possível iniciar apenas o banco com `docker compose up -d db`.

As variáveis necessárias estão em `.env.example`:

| Variável | Uso |
| --- | --- |
| `DB_URL` | URL JDBC, por exemplo `jdbc:postgresql://localhost:5432/nuser` |
| `DB_USERNAME` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Senha do PostgreSQL |
| `API_KEY` | Chave exigida no header `X-API-Key` |

O Spring Boot não carrega o `.env` automaticamente. Configure o arquivo como
fonte de variáveis na configuração de execução do IntelliJ ou exporte os valores
no terminal antes de iniciar:

```sh
./mvnw spring-boot:run
```

## Endpoints e autenticação

Todas as rotas exigem `X-API-Key`. Chave ausente ou inválida retorna 401.
A chave identifica o cliente da API; os usuários cadastrados não são contas de
autenticação dessa API. Fora do ambiente local, o tráfego deve usar HTTPS.

| Método | Rota | Comportamento |
| --- | --- | --- |
| POST | `/users` | Cadastra nome, email e senha; retorna 201 |
| GET | `/users/{id}` | Consulta por ID; retorna 200 ou 404 |
| GET | `/users?page=0&size=10&name=mar` | Lista com paginação e filtro opcional por parte do nome |
| PATCH | `/users/{id}` | Atualiza nome e/ou senha; retorna 200 ou 404 |


Exemplo de cadastro:

```json
{
  "username": "Marvin",
  "email": "marvin@example.com",
  "password": "examplePassword123"
}
```

Exemplo de resposta:

```json
{
  "status": 201,
  "data": {
    "id": 1,
    "username": "Marvin",
    "email": "marvin@example.com"
  }
}
```

O arquivo `requests.http` contém exemplos para o HTTP Client do IntelliJ.
Configure uma chave local para usá-los e não publique credenciais reais.


## Testes e motivação da cobertura

### UserServiceTest

| Teste | Motivação |
| --- | --- |
| `shouldCreateUserWithEncodedPasswordAndScheduleEmail` | Verificar que o cadastro passa a senha pelo encoder, salva os dados e agenda o email sem enviá-lo imediatamente. |
| `shouldNotScheduleEmailWhenCreationFails` | Impedir o agendamento do email quando o repository rejeita o cadastro. |
| `shouldNotPropagateConfirmationEmailFailure` | Verificar que uma exceção no envio é contida pelo callback, sem ser propagada. |
| `shouldReturnUserWhenIdExists` | Conferir os dados retornados para um usuário existente. |
| `shouldThrowWhenUserDoesNotExist` | Sinalizar explicitamente a ausência do usuário. |
| `shouldListUsersWithoutNameFilter` | Consultar sem filtro quando o nome é nulo, vazio ou contém somente espaços. |
| `shouldListUsersWithTrimmedNameFilter` | Remover espaços das extremidades do filtro e preservar a paginação solicitada. |
| `shouldReturnEmptyPageWhenNoUsersMatch` | Retornar uma coleção vazia com metadados coerentes, sem tratar a ausência de resultados como erro. |
| `shouldUpdateOnlyUsernameAndScheduleEmail` | Alterar somente o nome, preservar email e senha e agendar o email de atualização. |
| `shouldUpdateOnlyPassword` | Alterar a senha usando o encoder, preservando nome e email. |
| `shouldNotUpdateMissingUser` | Evitar persistência e agendamento de email para usuário inexistente. |
| `shouldNotScheduleEmailWhenUpdateFails` | Impedir o agendamento do email quando a persistência da edição falha. |

### UserControllerTest

| Teste | Motivação |
| --- | --- |
| `shouldCreateUser` | Conferir o status 201, o contrato da resposta e a ausência da senha. |
| `shouldRejectInvalidCreation` | Retornar 400 antes de chamar o service para nome vazio ou acima de 150 caracteres, senha vazia ou acima de 72 bytes (ASCII e acentos), email inválido ou vazio. |
| `shouldReturnConflictForDuplicateEmail` | Traduzir a violação de unicidade do email para 409 com mensagem compreensível. |
| `shouldGetUserById` | Conferir o status 200 e os dados públicos do usuário, sem senha. |
| `shouldReturnNotFound` | Traduzir a ausência do usuário consultado para 404. |
| `shouldListUsersWithFilterAndPagination` | Conferir o encaminhamento do filtro, página, tamanho e ordenação, além do contrato da resposta paginada. |
| `shouldUseDefaultPagination` | Conferir os padrões de página zero, tamanho 20 e ordenação por ID, com resposta vazia válida. |
| `shouldUpdateOnlyUsername` | Aceitar uma edição parcial contendo apenas o nome. |
| `shouldUpdateOnlyPassword` | Aceitar uma edição parcial contendo apenas a senha, sem expô-la na resposta. |
| `shouldRejectInvalidUpdate` | Retornar 400 antes de chamar o service para nome ou senha em branco, nome acima de 150 caracteres e senha acima de 72 bytes (ASCII e acentos). |
| `shouldReturnNotFoundWhenUpdatingMissingUser` | Retornar 404 ao tentar editar usuário inexistente. |
| `shouldHandleUnexpectedFailure` | Retornar 500 com mensagem genérica, sem expor detalhes internos. |
| `shouldAcceptPasswordAtByteLimitOnCreation` | Aceitar o limite exato de 72 bytes no cadastro, com caracteres ASCII e acentuados. |
| `shouldAcceptPasswordAtByteLimitOnUpdate` | Aceitar o limite exato de 72 bytes na edição, com caracteres ASCII e acentuados. |

### ApiKeyAuthenticationFilterTest

| Teste | Motivação |
| --- | --- |
| `shouldAuthenticateWithValidApiKey` | Autenticar a chave válida, permitir a requisição e limpar a autenticação ao final. |
| `shouldRejectMissingApiKey` | Bloquear requisição sem chave com 401 e o contrato de resposta esperado. |
| `shouldRejectInvalidApiKey` | Bloquear com 401 chaves incorretas, vazias ou contendo somente espaços. |
| `shouldRejectInvalidConfiguredKey` | Impedir que o filtro seja criado com uma chave configurada nula, vazia ou contendo somente espaços. |
| `shouldClearContextWhenFilterChainFails` | Evitar que a autenticação permaneça no contexto quando o processamento seguinte lança uma exceção. |

### NuserApplicationTests

| Teste | Motivação |
| --- | --- |
| `contextLoads` | Verificar que o contexto completo da aplicação inicializa com as dependências e configurações disponíveis. |

Os testes parametrizados repetem os cenários com entradas diferentes. Os testes
unitários do service executam os callbacks manualmente; não comprovam commit ou
rollback reais nem reenvio de email. O controller e o filtro são testados separadamente.

```sh
./mvnw test
```

## Possibilidades de frontend

Para a implementação do frontend, minha recomendação é utilizar Next.js 16 com SSR, focando em validações com schemas do Zod e Server Actions seguras para evitar a exposição de requisições no frontend, além de melhorar consideravelmente o desempenho.