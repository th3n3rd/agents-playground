# http4k A2A (Agent2Agent Protocol)

This example demonstrates how to build and test an A2A agent using http4k.

## What's included

- **RunA2AAgentAndClient.kt** — A client that connects to the running agent and streams responses
- **RecipeAgentTest.kt** — In-memory tests using `testA2AJsonRpcClient()` — no server needed

Tests run fully in-memory — no network, no ports.
