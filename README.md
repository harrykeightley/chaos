# The `Chaos` game engine
What would game development look like if the engine made it as [_simple_](https://www.youtube.com/watch?v=SxdOUGdseq4) as possible? 
- It would probably use a variant of [ECS](https://github.com/SanderMertens/ecs-faq)
- It would be Functional (as in functional programming)
- Devs wouldn't require deep knowledge of engine internals to use it;
- But, the engine internals should be easy to learn in depth.

Ideally, in addition to the previous goals:
- The technology should facilitate cross-platform development.
- On the fly additions/changes to game code should be seamless. (No lengthy recompilation) 

**The chaos engine was borne to achieve these goals.**

## Getting started
todo

## Engine Architecture
- [Sparse Sets](https://programmingpraxis.com/2012/03/09/sparse-sets/) are used for component storage.
- To be pure functions, our systems must be free of side effects. They therefore have to return the effect of running them. In the `Chaos` engine, systems are
  functions which take in the existing `World` state as a parameter, and return a series of `Commands` to apply to that `World`. e.g. informally, a command might say
  "Add component HP(3) to entity with id 1", but it doesn't have any notion of actually applying that to the `World`. It is therefore fully declarative. 
- Systems are grouped into stages which run sequentially based in the inter-stage dependencies.
- Within stages, systems are run in parallel based on their dependencies.

## Examples
Take a look at snake.cljc within the examples folder.

## Game lifecycle
When the game runs, it follows a set stage ordering:
- Once per game, the `:start-up` and `:tear-down` are run, at the beginning and end of the game respectively.
- In between these stages, the game will continually `step` forward until it detects that the game is over.
- A `:pre-step` stage and `:post-step` stage run before and after each `step`, respectively.
- Similarly, for each stage, a `:pre-stage` and `:post-stage` stage are run.
- Of the remaining stages, the `:update` stage is run first.
- Then, each of the remaining stages is run in order, based on their dependencies.

### Example Lifecycle
For a game which ends after one step, the lifecycle might look a little something like this:
- `:startup`
  - `:pre-step`
    - `:pre-stage`
      - `:update`
    - `:post-stage`
    - `:pre-stage`
      - `:render` (user defined stage)
    - `:post-stage`
  - `:post-step`
- `:tear-down`

### Reserved stages
```clojure
#{:start-up :pre-step :pre-stage :update :post-stage :post-step :tear-down}
```
