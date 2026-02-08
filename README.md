# AI Feed - Personalized Content Discovery App

An Android app that creates personalized article feeds based on user interests.

## Features

- **Personalized Feed**: Articles tailored to your interests with AI-powered recommendations
- **Topic Selection**: Choose topics you care about during onboarding
- **Smart Learning**: The app learns from your reading behavior to improve recommendations
- **Offline Support**: Read cached articles without internet connection
- **Bookmarks & History**: Save articles for later and track your reading history
- **Search**: Full-text search across all articles
- **Dark Mode**: Eye-friendly dark theme support
- **Trending on X**: AI-clustered real-time topics from authoritative X accounts

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
| Content Sources | NewsAPI, RSS feeds, X API |
| AI Summarization | Gemini |

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

The feed uses a personalized recommendation system that learns from user behavior.

### Relevance Score Calculation

Each article's relevance is calculated as:

```
relevanceScore = (topicWeight × 0.6) + (sourceWeight × 0.4)
```

- **Topic Weight (60%)**: Learned preference for the article's category
- **Source Weight (40%)**: Learned preference for the news source

### Interaction Signals

User interactions adjust both topic and source weights:

| Signal | Weight Delta | Effect |
|--------|-------------|--------|
| Like | +0.4 | Promotes similar content |
| Bookmark | +0.5 | Strong positive signal |
| Share | +0.6 | Strongest positive signal |
| Read > 5s | +0.2 | Moderate engagement |
| Click | +0.1 | Light interest |
| Dislike | -1.0 | Demotes similar content |

### How It Works

1. **Initial Setup**: User selects topics during onboarding (weight = 1.0)
2. **Learning**: Each interaction updates topic and source weights
3. **Scoring**: New articles are scored based on learned preferences
4. **Ranking**: Feed is sorted by relevance score (highest first)
5. **Adaptation**: Weights are clamped between 0.1 and 3.0 to prevent extreme bias

### Similar Articles

The "More like this" section shows articles that match:
- Same topic (primary signal)
- Same source (secondary signal)
- Not yet read by the user

## Trending On X

The **Trending on X** tab surfaces concrete, event-driven topics from trusted accounts and summarizes them with Gemini.

### Data Pipeline

1. Pull recent posts from curated authoritative accounts (news wires, institutions, major tech/company accounts)
2. Score posts by engagement + source quality (trust weight, follower signal, verification bonus)
3. Build a fast first-pass topic snapshot and render it immediately
4. Run Gemini refinement in the background to improve event clustering and summaries
5. Persist topics/posts in Room for smooth tab switching and detail navigation

### UX Behavior

- Topic cards show an AI disclaimer: `Summarized by AI - may contain errors`
- Progressive list loading:
  - Show first 5 topics immediately
  - Auto-reveal in batches of 5 until 20
  - After 20, manual `Load more` reveals 5 additional topics each time
- Topic detail view shows all related X posts for that topic

### Refresh Policy

- No automatic refresh when switching tabs or returning from topic detail
- On app reopen, refresh only if cached Trending data is older than 2 hours
- Pull-to-refresh is available for manual updates at any time

## Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## License

MIT License - See LICENSE file for details
