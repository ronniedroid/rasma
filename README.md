# Rasma

**A declarative language for describing GTK interfaces.**

Rasma is an EDN-inspired language for describing GTK interfaces using a concise, expressive syntax. It aims to provide a developer-friendly alternative to writing GtkBuilder XML directly, while retaining compatibility with the GTK ecosystem.

Rasma source files (`.rsm`) are compiled into standard GtkBuilder `.ui` XML files.

## Goals

Rasma aims to provide:

- **Readable syntax:** Describe GTK interfaces using a structured, Lisp-inspired syntax.
- **GtkBuilder compatibility:** Generate standard GtkBuilder XML that works with existing GTK applications.
- **API validation:** Validate GTK, GLib, GObject and libadwaita types, properties, signals and child relationships.
- **Useful diagnostics:** Provide clear, source-aware errors.
- **Build integration:** Support integration with Meson and existing GTK development workflows.
- **Developer tooling:** Eventually provide formatting, import capabilities and graphical editor tooling.

## Example

```clojure
(using Gtk 4.0)
(using Adw 1)

(callback window_close_request)

(template MainWindow
  :extends Adw.ApplicationWindow

  :title "Hello, Rasma!"
  :default-width 600

  (Adw.ToolbarView
    (:top
      (Adw.HeaderBar))

    :content
      (Gtk.Box
        :orientation vertical
        :spacing 12

        (Gtk.Label
          :label "Hello, Rasma!")))

  (on close-request window_close_request))
```

Rasma distinguishes between GObject properties, direct child objects and named child relationships. These correspond to different GtkBuilder concepts and are represented explicitly in the language.

## Design

Rasma takes inspiration from EDN and Clojure's data-oriented approach to syntax, but it is designed specifically for GTK interface descriptions rather than general-purpose programming.

The language provides:

- Lists for language forms and GTK objects.
- Vectors for ordered collections.
- Maps for future language features (accepted by the reader but not yet supported by the analyzer).
- Keywords for properties and other named options.
- Symbols for type names, identifiers and enum values.
- Explicit forms for templates, callbacks, custom types, signals, bindings, property mutations and translation.

Its intended compilation pipeline is:

```text
.rsm source
    |
    v
Reader
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

The reader preserves source locations in the AST. This allows later compiler stages to report errors against the original source.

GObject Introspection (GIR) metadata will eventually allow Rasma to validate GTK, GLib, GObject and libadwaita APIs.

## Implementation

Rasma is written in **Clojure**, running on the JVM.

The project is intended to provide a reusable compiler library alongside a command-line interface. Keeping the compiler independent of a graphical application will allow other tools and build systems to integrate with it.

## Development

### Requirements

- Java Development Kit (JDK)
- Clojure CLI

### Running

Clone the repository:

```bash
git clone https://github.com/ronniedroid/rasma.git
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

**Early development**

The initial source reader is taking shape. Current functionality includes:

- Reader state and source-position tracking.
- Unicode-aware column tracking.
- Whitespace and comment handling.
- Token extraction and classification.
- Strings and supported escape sequences.
- Lists, vectors and maps.
- Signed 64-bit integer validation.
- Floating-point overflow and underflow validation.
- Detection of unsupported syntax.
- Structured reader errors with source spans.
- Human-readable diagnostic rendering with source excerpts.

The AST and reader provide the foundation for the next stages of the compiler.

Semantic analysis, GIR-based validation and GtkBuilder XML generation are still under development.

## Roadmap

- [x] Establish the Clojure project structure.
- [x] Introduce AST nodes and source spans.
- [x] Implement reader state and token handling.
- [x] Implement strings and escape sequences.
- [x] Implement list, vector and map reading.
- [x] Implement lexical validation and numeric range checks.
- [x] Introduce structured errors.
- [x] Implement human-readable diagnostic rendering.
- [ ] Implement semantic analysis and validation.
- [ ] Validate GTK APIs using GIR metadata.
- [ ] Generate GtkBuilder XML.
- [ ] Integrate with Meson.
- [ ] Implement formatting and `.ui` import.
- [ ] Explore graphical editor tooling.

## Philosophy

Rasma is intended to complement GTK and GtkBuilder, not replace them.

The goal is to make describing GTK interfaces more pleasant, provide better diagnostics, and preserve compatibility with the tools and libraries GTK developers already use.

## License

Rasma is free software licensed under the **GNU General Public License v3.0 or later** (GPL-3.0-or-later).

See the [LICENSE](LICENSE) file for the full license text.
