### The Way Configuration Is Done Hurts

There is a lot of common ceremony/boilerplate in a lot of projects - setting up REST services, or ETL functions, etc.

This project provides the main components to drive a "black-box" computation - typically which operates on json messages.

This is a typical software component project life-cycle:

```
1. bootstrap a new project (or copy/clone some exististing project)
2. hack around with all the usual stuff (configuration parsing, auth, routing, packaging, ...)
3. finally get to the business logic you wanted to solve
```

And then the iterations - with a new feature/behavior, you often have to:

```
1. introduce some new property (e.g., perhaps an `app.properties` has a myApp.newFeature.isEnabled = true)
2. change the config parsing (the code which knows how to look for myApp.newFeature.isEnabled)
3. implement some logic based on that configuration
```

# Usage

Imagine we're writing an application which knows how to do efficient, resource-safe batch processing on a stream of data.

That application might use this library to do something like this:
```
val compiler       = CodeTemplate.newCache[Json, Json]()
```

That compiler is a function which takes a string (e.g. some business logic script) and returns the __business logic__ - a function
which takes some input _Context_ and returns the result type (e.g. some Json in this case).

```
     // in practice, this script wouldn't be hard-coded, but come an application configuration, such as a kubernetes config map,
     // a cloud bucket - whatever.
     val script : String = """
           val result = input.content.hello.world // <-- here we would implment our business logic - compute a value, call a service, whatever
           result.asJson
      """
      
      // at the usage site, that script would be attempted to be compiled (hence the Try) into an Expression, 
      // which is a function of some 'Context[Input]' into the result type.
      //
      // here we have normal Json input and Json output:
      val compiledResult: Try[Expression[Json, Json]] = compiler(script)
```

