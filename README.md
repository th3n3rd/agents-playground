# http4k A2A (Agent2Agent Protocol)

This example demonstrates how to build and test an A2A agent using http4k.

## What's included

- **RunA2AAgentAndClient.kt** — A client that connects to the running agent and streams responses
- **RecipeAgentTest.kt** — In-memory tests using `testA2AJsonRpcClient()` — no server needed

Tests run fully in-memory — no network, no ports.


## Learnings

- The Agent itself should be "dumb", i.e. not actually executing capabilities
  - Skills are not explicitly invoked, so it's not easy to understand in code how we would handle a given request
  - Message will be delegated to an LLM for processing
  - LLMs will then interpret the intent and decide whether an MCP capability, e.g. tool, needs to be invoked
  - The business logic lives on the MCP side, not the Agent side

- Agent responses are typically streamed

- The pattern is typically Agent orchestrator + Sub-agents for different domains/boundary

