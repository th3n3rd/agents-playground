# Provides all recipes for a carbonara

```mermaid    
sequenceDiagram
    client->>+cooking-assistant: POST 
    cooking-assistant->>+recipe-agent: task
    recipe-agent->>+recipes-mcp: tools/call search_recipes
    recipes-mcp-->>-recipe-agent: 
    recipe-agent-->>-cooking-assistant: 
    cooking-assistant-->>-client: 200
    participant client as Client
    participant cooking-assistant as Cooking Assistant
    participant recipe-agent as Recipe Agent
    participant recipes-mcp as Recipes Mcp

```