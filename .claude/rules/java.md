# Regras — Java / Spring Boot (carimbai)

Carregadas ao editar arquivos `.java`. Seguir as convenções já presentes no
código (`com.app.carimbai`).

## Camadas
- **Controllers** são finos: validam entrada (Bean Validation nos DTOs) e
  delegam para `services`/`facade`. Nunca acessam `repositories` diretamente nem
  contêm regra de negócio.
- **Services / facade**: regra de negócio e orquestração. Anotar com
  `@Transactional` operações que escrevem em mais de uma entidade.
- **Repositories**: Spring Data JPA. Preferir derived queries; usar `@Query`
  parametrizada quando necessário (nunca concatenar string em query — risco de
  injeção).

## DTOs & mapeamento
- Requests/responses são DTOs (pastas `dtos/`, com `admin/`, `customer/`,
  `login/`, `redeem/`). Não expor entidades JPA diretamente na API.
- Conversão entidade↔DTO **sempre via MapStruct** (`mappers/`). Não escrever
  mapeamento manual quando há mapper.
- Validar requests com Bean Validation (`@NotNull`, `@Email`, `@Valid`, etc.).

## Construtores
- Não usar @Autowired para injeção de dependecia

## Boilerplate
- Usar **Lombok** (`@Getter/@Setter/@Builder/@RequiredArgsConstructor`) no estilo
  já adotado. Injeção de dependência por construtor.

## Erros & segurança
- Lançar exceções de domínio (pasta `execption/` — typo herdado, manter) e
  deixar o **handler central** (`handler/`) traduzir para resposta HTTP. Não
  montar `ResponseEntity` de erro espalhado pelos controllers.
- **Toda lógica de autenticação/autorização fica em `security/`.** Não duplicar
  verificação de token fora dali.
- Autorização por papel usa `StaffRole` (enum). Checar papel antes de operações
  administrativas.
- **Segredos sempre via configuração** (`@Value`/`@ConfigurationProperties`,
  env vars `CARIMBAI_*`). Nunca hardcodar chave/secret em `.java`.
- Tokens de selo são assinados por HMAC SHA-256 — validar assinatura **e**
  expiração antes de aplicar selo; respeitar idempotência (`IdempotencyKey`).

## Estilo
- Pacote base `com.app.carimbai`; manter a organização por subpacote existente.
- Não introduzir frameworks novos sem necessidade clara.
