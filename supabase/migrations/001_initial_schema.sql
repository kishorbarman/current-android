-- AI Feed Database Schema
-- Initial migration

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- Users table (synced from Firebase Auth)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    display_name TEXT,
    avatar_url TEXT,
    onboarding_completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create index on email for lookups
CREATE INDEX idx_users_email ON users(email);

-- Topics hierarchy table
CREATE TABLE topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT UNIQUE NOT NULL,
    icon TEXT,
    parent_topic_id UUID REFERENCES topics(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create index for topic lookups
CREATE INDEX idx_topics_slug ON topics(slug);
CREATE INDEX idx_topics_parent ON topics(parent_topic_id);

-- Articles table with full-text search and vector embeddings
CREATE TABLE articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    preview TEXT,
    content TEXT,
    source_url TEXT UNIQUE NOT NULL,
    image_url TEXT,
    source_name TEXT,
    author TEXT,
    published_at TIMESTAMPTZ,
    primary_topic_id UUID REFERENCES topics(id) ON DELETE SET NULL,
    embedding VECTOR(384),  -- For similarity search using sentence-transformers
    moderation_status TEXT DEFAULT 'approved' CHECK (moderation_status IN ('pending', 'approved', 'rejected')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for article queries
CREATE INDEX idx_articles_topic ON articles(primary_topic_id);
CREATE INDEX idx_articles_published ON articles(published_at DESC);
CREATE INDEX idx_articles_source_url ON articles(source_url);
CREATE INDEX idx_articles_moderation ON articles(moderation_status);

-- Full-text search index
CREATE INDEX idx_articles_fts ON articles USING gin(to_tsvector('english', title || ' ' || COALESCE(preview, '')));

-- User topic preferences (many-to-many with weights)
CREATE TABLE user_topics (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    topic_id UUID REFERENCES topics(id) ON DELETE CASCADE,
    weight FLOAT DEFAULT 1.0 CHECK (weight >= 0 AND weight <= 5),
    selected_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, topic_id)
);

-- Create index for user topic lookups
CREATE INDEX idx_user_topics_user ON user_topics(user_id);
CREATE INDEX idx_user_topics_weight ON user_topics(user_id, weight DESC);

-- Interaction tracking table
CREATE TABLE interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    article_id UUID REFERENCES articles(id) ON DELETE CASCADE,
    interaction_type TEXT NOT NULL CHECK (interaction_type IN ('click', 'read', 'bookmark', 'unbookmark', 'share', 'like', 'dislike')),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for interaction queries
CREATE INDEX idx_interactions_user ON interactions(user_id, created_at DESC);
CREATE INDEX idx_interactions_article ON interactions(article_id);
CREATE INDEX idx_interactions_type ON interactions(user_id, interaction_type);

-- Article sources for content ingestion
CREATE TABLE article_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    source_type TEXT NOT NULL CHECK (source_type IN ('newsapi', 'rss', 'reddit')),
    url TEXT NOT NULL,
    topic_id UUID REFERENCES topics(id) ON DELETE SET NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_fetched_at TIMESTAMPTZ,
    fetch_interval_minutes INT DEFAULT 30,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_sources_active ON article_sources(is_active, last_fetched_at);

-- User reading sessions for analytics
CREATE TABLE reading_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    article_id UUID REFERENCES articles(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    scroll_percentage FLOAT CHECK (scroll_percentage >= 0 AND scroll_percentage <= 100),
    time_spent_seconds INT
);

CREATE INDEX idx_sessions_user ON reading_sessions(user_id, started_at DESC);

-- Push notification tokens
CREATE TABLE push_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    device_type TEXT CHECK (device_type IN ('android', 'ios', 'web')),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, token)
);

CREATE INDEX idx_push_tokens_user ON push_tokens(user_id, is_active);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add triggers for updated_at
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_articles_updated_at
    BEFORE UPDATE ON articles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_push_tokens_updated_at
    BEFORE UPDATE ON push_tokens
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Row Level Security policies
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE topics ENABLE ROW LEVEL SECURITY;
ALTER TABLE articles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_topics ENABLE ROW LEVEL SECURITY;
ALTER TABLE interactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE reading_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE push_tokens ENABLE ROW LEVEL SECURITY;

-- Topics are public read
CREATE POLICY "Topics are viewable by everyone" ON topics FOR SELECT USING (true);

-- Articles are public read
CREATE POLICY "Approved articles are viewable by everyone" ON articles
    FOR SELECT USING (moderation_status = 'approved');

-- Users can view and update their own data
CREATE POLICY "Users can view own data" ON users
    FOR SELECT USING (auth.uid()::text = id::text);

CREATE POLICY "Users can update own data" ON users
    FOR UPDATE USING (auth.uid()::text = id::text);

-- User topics policies
CREATE POLICY "Users can view own topics" ON user_topics
    FOR SELECT USING (auth.uid()::text = user_id::text);

CREATE POLICY "Users can insert own topics" ON user_topics
    FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);

CREATE POLICY "Users can update own topics" ON user_topics
    FOR UPDATE USING (auth.uid()::text = user_id::text);

CREATE POLICY "Users can delete own topics" ON user_topics
    FOR DELETE USING (auth.uid()::text = user_id::text);

-- Interactions policies
CREATE POLICY "Users can view own interactions" ON interactions
    FOR SELECT USING (auth.uid()::text = user_id::text);

CREATE POLICY "Users can insert own interactions" ON interactions
    FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);

-- Reading sessions policies
CREATE POLICY "Users can view own sessions" ON reading_sessions
    FOR SELECT USING (auth.uid()::text = user_id::text);

CREATE POLICY "Users can insert own sessions" ON reading_sessions
    FOR INSERT WITH CHECK (auth.uid()::text = user_id::text);

CREATE POLICY "Users can update own sessions" ON reading_sessions
    FOR UPDATE USING (auth.uid()::text = user_id::text);

-- Push tokens policies
CREATE POLICY "Users can manage own push tokens" ON push_tokens
    FOR ALL USING (auth.uid()::text = user_id::text);
