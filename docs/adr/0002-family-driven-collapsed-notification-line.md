# The collapsed notification line is family-driven; the expanded view stays fixed

The ongoing notification's **collapsed** line renders from a user-ordered registry of metric *families* (Speed, Current, Time, Temperature, Power source), each family rendering its own state-appropriate form; the **expanded** view is deliberately left out of that model and stays fixed (#356). The expanded view's lines are not families — `Live` is current *plus* watts, `Average` is avg current *plus* %/h — so honouring the user's selection there means restructuring the view first, and #193 and #354 are already rewriting exactly those lines. Extending the family model to it now means writing the restructure twice and conflicting with two open issues.

## Considered Options

- **Filter the expanded view by the selection** — rejected outright on its own merits. The expanded view being the "everything" view is what makes the collapsed line's silent segment-collapse acceptable, so ordering it is a future option and hiding from it is not.

## Consequences

`OngoingStatusContent.statusDetail()` renders from the ordered selection; `statusDetailExpanded()` keeps its fixed Live/Average/Time/Temperature shape and never reads the selection. Nor does it read the show-rate switch the selection replaced (#357), although that switch used to hide its rate lines: a user who had turned it off keeps a temperature-only collapsed line but now gets the full expanded view. Ordering the expanded view by the same selection, giving it the `calculating` hints the details table already has (#216), and whether its rate lines should be switchable again are tracked in #361 and blocked on #193 and #354.
