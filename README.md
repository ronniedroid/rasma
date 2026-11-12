# Rasma

**A declarative language for describing GTK interfaces.**

Rasma is an EDN-inspired language for describing GTK interfaces using a concise, expressive syntax. It aims to provide a developer-friendly alternative to writing GtkBuilder XML directly, while retaining compatibility with the GTK ecosystem.

Rasma source files (`.rsm`) are intended to be compiled into standard GtkBuilder `.ui` XML files.

## Goals

Rasma aims to provide:

- **Readable syntax:** Describe GTK interfaces using a structured, Lisp-inspired syntax.
- **GtkBuilder compatibility:** Generate standard GtkBuilder XML that works with existing GTK applications.
- **API validation:** Validate GTK, GLib, GObject, and libadwaita types, properties, signals, and relationships.
- **Useful diagnostics:** Provide clear errors with source locations.
- **Build integration:** Support integration with Meson and existing GTK development workflows.
- **Developer tooling:** Eventually provide formatting, import capabilities, and a graphical editor.

## Example

The following illustrates the intended Rasma syntax:

```clojure
(using Gtk 4.0)
(using Adw 1)

(template MyWindow :extends Adw.ApplicationWindow
  :title "Hello, Rasma!"
  :default-width 600

  (Adw.HeaderBar)

  :content
    (Gtk.Box
      :orientation vertical

      (Gtk.Label
        :label "Hello, Rasma!")))
```

*This is a design example. The complete parser and compiler are still under development.*

## Design

Rasma takes inspiration from EDN and Clojure's data-oriented approach to syntax, but it is designed specifically for GTK interface descriptions rather than general-purpose programming.

Its intended compilation pipeline is:

```text
.rst source
    |
    v
Reader / Parser
    |
    v
Abstract Syntax Tree
    |
    v
Analysis and Validation
    |
    v
GtkBuilder XML Emitter
    |
    v
.ui output
```

GTK API metadata, primarily sourced from GObject Introspection (GIR), will eventually allow Rasma to validate interface definitions against the actual GTK ecosystem.

## Implementation

Rasma is written in **Clojure**, running on the JVM.

The project is structured around a reusable compiler library and a command-line interface. Keeping the compiler independent of a graphical application will allow other tools and build systems to integrate with it.

## Development

### Requirements

- Java Development Kit (JDK)
- Clojure CLI

### Running

Clone the repository:

```bash
git clone https://github.com/ronniedroid/rasma
cd rasma
```

Run the project:

```bash
clojure -M -m rasma.core
```

### Running tests

```bash
clojure -M:test
```

## Project status

<mark>Early development</mark>

Rasma is actively being developed. The initial reader implementation currently supports:

- Reader state and source-position tracking.
- Whitespace and comment handling.
- Token extraction.
- Classification of symbols, keywords, integers, floating-point numbers, booleans, and nil.
- AST node creation with source spans.

The complete parser, semantic analysis, validation, and XML generation stages have not yet been implemented.

## Roadmap

- [x] Establish the Clojure project structure.
- [x] Implement initial reader state and token handling.
- [x] Introduce AST nodes and source spans.
- [ ] Implement string literals and escape sequences.
- [ ] Implement collection parsing.
- [ ] Complete the source-aware reader and parser.
- [ ] Implement semantic analysis and validation.
- [ ] Generate GtkBuilder XML.
- [ ] Add GIR-based GTK API validation.
- [ ] Integrate with Meson.
- [ ] Implement formatting and `.ui` import.
- [ ] Explore graphical editor tooling.

## Philosophy

Rasma is intended to complement GTK and GtkBuilder, not replace them.

The goal is to make describing GTK interfaces more pleasant, provide better diagnostics, and preserve compatibility with the tools and libraries GTK developers already use.

## License

Rasma is free software licensed under the **GNU General Public License v3.0 or later** (GPL-3.0-or-later).

See the [LICENSE](LICENSE) file for the full license text.
