# Política de Segurança

## Versões suportadas

Apenas o commit mais recente da branch `main` é suportado.

## Como reportar uma vulnerabilidade

Não abra uma issue pública. Use o botão **Report a vulnerability** do GitHub (aba Security, relato privado de
vulnerabilidades) neste repositório. Inclua os passos para reproduzir e o endpoint ou arquivo afetado.
Você pode esperar uma resposta em alguns dias; as correções são publicadas junto com o aviso de segurança.

## O que o projeto faz

- Senhas com hash bcrypt; o login segue o mesmo caminho para e-mails inexistentes (sem enumeração de usuários por
  tempo de resposta ou mensagem).
- JWT sem estado (HMAC, emissor fixo, tamanho da assinatura verificado), validade padrão de 60 minutos. O servidor
  se recusa a iniciar com um `JWT_SECRET` ausente, curto ou de exemplo.
- Toda tarefa, endereço e telefone é buscado a partir do seu dono, então objetos de outros usuários retornam 404.
- Limite de tentativas em `/api/auth/**` (por endereço do cliente), corpo de requisição de no máximo 64 KiB, tetos por
  usuário de tarefas (1000), endereços (10) e telefones (10), e tamanho de página limitado.
- Content-Security-Policy estrita na página incluída, além de `nosniff`, bloqueio de frames e `no-referrer`.
- O contêiner roda como usuário não-root, com sistema de arquivos somente leitura e sem capacidades extras; os bancos
  escutam apenas em `127.0.0.1` e exigem credenciais vindas do `.env`.

## Limitações conhecidas

- **Sem revogação nem renovação de token.** Um JWT roubado funciona até expirar. Mantenha `JWT_EXPIRATION_MINUTES`
  curto.
- **O limite de tentativas fica em memória e usa o endereço TCP de origem.** Atrás de um proxy reverso, todos os
  clientes compartilham o endereço do proxy, e várias instâncias mantêm contadores separados. Em produção, coloque um
  gateway ou WAF com limite na frente.
- **Sem TLS e sem HSTS neste repositório.** Termine o HTTPS em um proxy reverso antes de expor a API.
- **A página web guarda o token em `sessionStorage`.** A CSP reduz o risco de XSS, mas não o elimina.
- **O Swagger UI e o `/v3/api-docs` são públicos.** Desative-os (`springdoc.api-docs.enabled=false`,
  `springdoc.swagger-ui.enabled=false`) em um ambiente de produção.
- **O esquema do banco é gerenciado pelo Hibernate com `ddl-auto: update`.** Migre para Flyway ou Liquibase antes de
  ir para produção.
- **O cadastro revela se um e-mail já existe** (resposta 409). Aceito para manter o cadastro utilizável.
- O projeto passou apenas por verificações automatizadas, não por um teste de invasão independente.
