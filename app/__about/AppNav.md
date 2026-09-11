# AppNav

The app's shell: a title bar, four tabs, one screen at a time.

## Responsibility

Own the navigation graph and the bottom bar, and be the single place where the
state holder is created and handed to screens.

## Decisions that outlive the code

- **Four destinations, and four is a ceiling.** Home, Recordings, Numbers,
  Settings. Every extra tab is one more thing to understand while frightened;
  anything that does not belong in these four belongs INSIDE one of them.
- **Setup is deliberately not a tab.** It is a flow reached from the one card
  that asks for it, and it disappears once it is done — the app should not carry
  a permanent reminder that something is unfinished.
- **Tabs are an enum, not four copy-pasted `NavigationBarItem` blocks** (ONE
  KIND, ONE CLASS). A new destination is one enum entry plus one `composable`.
- **State is restored per tab** (`saveState`/`restoreState`), so a half-typed
  search or a scroll position survives a trip to another tab — someone who is
  interrupted mid-task does not have to start over.

## Connections

- Creates: `ui/AppViewModel`.
- Hosts: the home, recordings, numbers, settings and setup screens.
