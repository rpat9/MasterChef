# MasterChef Frontend

A modern recipe discovery application built with React, TypeScript, and Vite. MasterChef helps users find recipes based on available ingredients and dietary preferences, featuring a warm, earthy design system inspired by natural cooking aesthetics.

## 📋 Table of Contents

- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Design System](#design-system)
- [Component Architecture](#component-architecture)
- [Development Guidelines](#development-guidelines)
- [Available Scripts](#available-scripts)
- [Code Style & Conventions](#code-style--conventions)
- [Key Features](#key-features)

## 🚀 Tech Stack

### Core Framework
- **React 19.2** - UI library with latest features including new JSX transform
- **TypeScript 5.9** - Type-safe JavaScript with strict mode enabled
- **Vite 7.2** - Next-generation frontend build tool

### UI & Styling
- **Tailwind CSS 4.1** - Utility-first CSS framework with custom design tokens
- **Radix UI** - Unstyled, accessible component primitives
- **shadcn/ui** - Re-usable component library built on Radix UI
- **Lucide React** - Beautiful icon library
- **Class Variance Authority** - Component variant management

### State Management & Data Fetching
- **TanStack Query 5.90** - Powerful data synchronization for React
- **React Hook Form 7.71** - Performant form validation
- **Zod 4.3** - TypeScript-first schema validation

### Routing & Navigation
- **React Router DOM 7.13** - Client-side routing

### Utilities
- **date-fns** - Modern date utility library
- **clsx & tailwind-merge** - Conditional className composition
- **sonner** - Toast notifications

## 📦 Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js** (v18 or higher)
- **npm** or **yarn** or **pnpm**

## 🏁 Getting Started

### Installation

1. Clone the repository and navigate to the frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm run dev
```

4. Open your browser and visit:
```
http://localhost:5173
```

### Building for Production

```bash
npm run build
```

The production-ready files will be generated in the `dist/` directory.

### Preview Production Build

```bash
npm run preview
```

## 📁 Project Structure

```
frontend/
├── public/              # Static assets
├── src/
│   ├── assets/         # Images, fonts, and other assets
│   ├── components/     # React components
│   │   ├── ui/        # Reusable UI components (shadcn/ui)
│   │   ├── layout/    # Layout components (Header, Footer, etc.)
│   │   └── recipe/    # Recipe-specific components
│   ├── hooks/         # Custom React hooks
│   │   ├── useMobile.tsx
│   │   └── useToast.ts
│   ├── lib/           # Utility functions and helpers
│   │   └── utils.ts   # cn() function for className merging
│   ├── pages/         # Route/page components
│   │   ├── Auth.tsx
│   │   ├── Dashboard.tsx
│   │   ├── Index.tsx
│   │   ├── NotFound.tsx
│   │   ├── Profile.tsx
│   │   └── SavedRecipes.tsx
│   ├── types/         # TypeScript type definitions
│   ├── App.tsx        # Root application component
│   ├── main.tsx       # Application entry point
│   └── index.css      # Global styles and Tailwind configuration
├── index.html         # HTML entry point
├── package.json       # Project dependencies and scripts
├── tsconfig.json      # TypeScript configuration
├── vite.config.ts     # Vite configuration
└── README.md          # This file
```

## 🎨 Design System

MasterChef features a custom design system with warm, earthy tones inspired by natural cooking aesthetics.

### Color Palette

**Light Mode:**
- **Primary (Sage)**: `hsl(142, 35%, 35%)` - Main brand color
- **Secondary (Amber)**: `hsl(35, 65%, 55%)` - Accent color
- **Background (Cream)**: `hsl(45, 33%, 97%)` - Base background
- **Foreground**: `hsl(25, 25%, 15%)` - Primary text

**Custom Tokens:**
- `sage` & `sage-light` - Green tones for primary actions
- `amber` & `amber-light` - Warm orange tones for accents
- `cream` - Soft background color
- `terracotta` - Earthy red-brown
- `olive` - Muted green-brown

### Typography

- Base font with antialiasing and font feature settings
- Headings use semibold weight with tight tracking
- Text balance utility for improved readability

### Theme Support

The application includes full dark mode support with automatically adjusted color values for optimal contrast and readability.

## 🧩 Component Architecture

### UI Components (src/components/ui/)

Pre-built, accessible components following the shadcn/ui pattern:

**Currently Used:**
- `button` - Interactive button with variants
- `card` - Container with header, content, footer sections
- `input` - Form input field
- `label` - Form label
- `badge` - Status/tag indicator
- `tabs` - Tabbed interface
- `sheet` - Slide-out panel
- `toast` - Notification system
- `separator` - Visual divider

**Available (40+ components):**
- Form controls: checkbox, radio-group, select, slider, switch, textarea
- Navigation: breadcrumb, menu-bar, navigation-menu, pagination
- Overlays: dialog, popover, tooltip, hover-card, context-menu
- Layouts: accordion, collapsible, resizeable, scroll-area, sidebar
- Feedback: alert, progress, skeleton
- And more...

### Component Pattern

All UI components follow this structure:

```tsx
import { forwardRef } from "react";
import type { ComponentPropsWithoutRef } from "react";
import { cn } from "../../lib/utils";

const Component = forwardRef<HTMLElement, ComponentPropsWithoutRef<"element">>(
    ({ className, ...props }, ref) => (
        <element
            ref={ref}
            className={cn("base-styles", className)}
            {...props}
        />
    )
);
Component.displayName = "Component";
```

**Key Patterns:**
- Use `forwardRef` for ref forwarding to DOM elements
- Import specific React functions instead of namespace imports
- Use HTML element types (e.g., `HTMLButtonElement`) instead of React.ElementRef
- Relative imports (`../../lib/utils`) instead of path aliases
- 4-space indentation throughout
- Type-only imports for types

### Custom Hooks

**`useMobile`**: Detects mobile viewport sizes

**`useToast`**: Toast notification management

## 💻 Development Guidelines

### Import Standards

```tsx
// Correct - Specific imports
import { forwardRef, useState } from "react";
import type { ComponentPropsWithoutRef } from "react";

// Avoid - Namespace imports
import * as React from "react";
import React from "react";
```

### Type Imports

```tsx
// Correct - Type-only imports
import type { ComponentPropsWithoutRef } from "react";
import { cva } from "class-variance-authority";
import type { VariantProps } from "class-variance-authority";

// Avoid - Mixed imports when verbatimModuleSyntax is enabled
import { VariantProps, cva } from "class-variance-authority";
```

### Path Resolution

Use relative imports instead of path aliases:

```tsx
// Correct
import { cn } from "../../lib/utils";
import { Button } from "../ui/button";

// Avoid
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
```

### Styling with `cn()`

The `cn()` utility combines `clsx` and `tailwind-merge`:

```tsx
<button className={cn(
    "base-styles",
    variant === "primary" && "primary-styles",
    className // User-provided classes
)} />
```

## 📜 Available Scripts

```bash
# Start development server (http://localhost:5173)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Run ESLint
npm run lint
```

## 📝 Code Style & Conventions

### Indentation
- **4 spaces** for all files (TypeScript, TSX, JSON)

### Naming Conventions
- **Components**: PascalCase (e.g., `RecipeCard.tsx`)
- **Hooks**: camelCase with "use" prefix (e.g., `useMobile.tsx`)
- **Utilities**: camelCase (e.g., `utils.ts`)
- **Types/Interfaces**: PascalCase (e.g., `Recipe`, `UserProfile`)

### File Organization
- One component per file
- Co-locate related components in subdirectories
- Export at the bottom of the file (except for default exports)

### Component Structure
```tsx
// 1. Imports (grouped: React, external libs, internal)
import { forwardRef } from "react";
import * as Primitive from "@radix-ui/react-something";
import { cn } from "../../lib/utils";

// 2. Types/Interfaces
interface ComponentProps extends ComponentPropsWithoutRef<"element"> {
    // custom props
}

// 3. Component definition
const Component = forwardRef<HTMLElement, ComponentProps>(
    ({ className, ...props }, ref) => {
        // logic here
        return (
            <element ref={ref} className={cn("styles", className)} {...props} />
        );
    }
);

// 4. Display name
Component.displayName = "Component";

// 5. Exports
export { Component };
```

## ✨ Key Features

### Current Implementation
- **Ingredient-based search**: Find recipes based on available ingredients
- **Dietary preferences**: Filter by vegetarian, vegan, gluten-free, dairy-free, nut-free
- **Popular ingredients**: Quick-add buttons for common ingredients
- **Recipe cards**: Visual recipe browsing with images
- **Saved recipes**: Bookmark favorite recipes
- **User profiles**: Personalized user accounts
- **Authentication**: Secure login and registration
- **Responsive design**: Mobile-first, works on all screen sizes
- **Dark mode support**: System-aware theme switching
- **Toast notifications**: User feedback for actions

### Component Reusability
The UI component library provides 40+ pre-built, accessible components that can be easily integrated into any part of the application. All components support:
- Custom className prop for style overrides
- Ref forwarding for DOM access
- TypeScript type safety
- Accessibility (ARIA) attributes
- Keyboard navigation
- Dark mode theming

## 🤝 Contributing

When contributing to this project:

1. Follow the established code style and conventions
2. Use 4-space indentation
3. Write descriptive commit messages
4. Test your changes thoroughly
5. Update documentation as needed
6. Ensure all TypeScript types are properly defined
7. Run `npm run lint` before committing

## 📄 License

This project is licensed under the MIT License.

---

**Built with React, TypeScript, and Vite**