# AI Feed - Personalized Content Discovery App

A Google Discover-like Android app that creates personalized article feeds based on user interests.

## Features

- **Personalized Feed**: Articles tailored to your interests with AI-powered recommendations
- **Topic Selection**: Choose topics you care about during onboarding
- **Smart Learning**: The app learns from your reading behavior to improve recommendations
- **Offline Support**: Read cached articles without internet connection
- **Bookmarks & History**: Save articles for later and track your reading history
- **Search**: Full-text search across all articles
- **Dark Mode**: Eye-friendly dark theme support

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Kotlin + Jetpack Compose |
| Architecture | MVVM + Clean Architecture |
| Local Database | Room |
| Backend | Supabase (PostgreSQL) |
| Auth | Firebase Auth + Credential Manager |
| Analytics | Firebase Analytics + Crashlytics |
| Push | Firebase Cloud Messaging |
| Content Sources | NewsAPI, RSS feeds |

## Project Structure

```
app/
├── src/main/java/com/aifeed/
│   ├── core/
│   │   ├── database/      # Room database, entities, DAOs
│   │   ├── network/       # Retrofit, API services
│   │   ├── di/            # Hilt modules
│   │   └── util/          # Extensions, helpers
│   ├── feature/
│   │   ├── auth/          # Google Sign-In
│   │   ├── onboarding/    # Topic selection
│   │   ├── feed/          # Main feed UI + logic
│   │   ├── article/       # Article detail/reader
│   │   ├── search/        # Search functionality
│   │   └── profile/       # Settings, bookmarks
│   ├── navigation/        # Navigation setup
│   ├── ui/theme/          # Material3 theming
│   ├── AiFeedApp.kt
│   └── MainActivity.kt
├── supabase/
│   ├── migrations/        # SQL schema
│   └── functions/         # Edge Functions
```

## Setup

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### External Services Setup

1. **Firebase Project**
   - Create a project at [Firebase Console](https://console.firebase.google.com)
   - Enable Authentication with Google Sign-In
   - Download `google-services.json` and place in `app/`

2. **Supabase Project**
   - Create a project at [Supabase](https://supabase.com)
   - Run the migrations in `supabase/migrations/`
   - Copy your project URL and anon key

3. **NewsAPI Key**
   - Register at [newsapi.org](https://newsapi.org)
   - Get your API key (free tier: 500 requests/day)

### Configuration

1. Copy `local.properties.template` to `local.properties`
2. Fill in your API keys and configuration:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
NEWS_API_KEY=your-newsapi-key
GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Architecture

The app follows Clean Architecture with MVVM:

```
UI Layer (Compose)
    ↓
ViewModel (State Management)
    ↓
Repository (Data Abstraction)
    ↓
Data Sources (Room DB, Retrofit API)
```

### Key Design Decisions

- **Offline-first**: Articles are cached locally, synced when online
- **Paging 3**: Efficient infinite scrolling with pagination
- **Flow/StateFlow**: Reactive data streams throughout
- **Hilt**: Dependency injection for testability

## Recommendation Algorithm

The feed uses a hybrid recommendation approach:

1. **Topic Relevance (40%)**: Based on user's selected topics and learned weights
2. **Freshness (30%)**: Exponential decay with 48-hour half-life
3. **Diversity (30%)**: Random factor to prevent filter bubbles

### Interaction Signals

| Signal | Weight Impact |
|--------|--------------|
| Click | +0.1 |
| Read > 30s | +0.2 |
| Bookmark | +0.5 |
| Share | +0.6 |
| Dislike | -1.0 |

## Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## License

MIT License - See LICENSE file for details
