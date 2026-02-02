-- Database functions for AI Feed

-- Function to get personalized feed for a user
CREATE OR REPLACE FUNCTION get_personalized_feed(
    p_user_id UUID,
    p_limit INT DEFAULT 50,
    p_offset INT DEFAULT 0
)
RETURNS TABLE (
    id UUID,
    title TEXT,
    preview TEXT,
    source_url TEXT,
    image_url TEXT,
    source_name TEXT,
    published_at TIMESTAMPTZ,
    primary_topic_id UUID,
    relevance_score FLOAT
) AS $$
BEGIN
    RETURN QUERY
    WITH user_topic_weights AS (
        SELECT topic_id, weight
        FROM user_topics
        WHERE user_id = p_user_id
    ),
    scored_articles AS (
        SELECT
            a.id,
            a.title,
            a.preview,
            a.source_url,
            a.image_url,
            a.source_name,
            a.published_at,
            a.primary_topic_id,
            -- Calculate relevance score
            COALESCE(utw.weight, 0.5) * 0.4 +  -- Topic weight (40%)
            -- Freshness score (30%) - exponential decay with 48h half-life
            EXP(-EXTRACT(EPOCH FROM (NOW() - a.published_at)) / (48 * 3600)) * 0.3 +
            -- Random factor for diversity (30%)
            RANDOM() * 0.3
            AS relevance_score
        FROM articles a
        LEFT JOIN user_topic_weights utw ON a.primary_topic_id = utw.topic_id
        WHERE a.moderation_status = 'approved'
            AND a.published_at > NOW() - INTERVAL '7 days'
            AND NOT EXISTS (
                -- Exclude already read articles
                SELECT 1 FROM interactions i
                WHERE i.article_id = a.id
                    AND i.user_id = p_user_id
                    AND i.interaction_type = 'read'
            )
    )
    SELECT
        sa.id,
        sa.title,
        sa.preview,
        sa.source_url,
        sa.image_url,
        sa.source_name,
        sa.published_at,
        sa.primary_topic_id,
        sa.relevance_score
    FROM scored_articles sa
    ORDER BY sa.relevance_score DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to update topic weights based on interactions
CREATE OR REPLACE FUNCTION update_topic_weight_from_interaction()
RETURNS TRIGGER AS $$
DECLARE
    v_topic_id UUID;
    v_weight_delta FLOAT;
    v_current_weight FLOAT;
    v_new_weight FLOAT;
BEGIN
    -- Get the topic of the article
    SELECT primary_topic_id INTO v_topic_id
    FROM articles
    WHERE id = NEW.article_id;

    IF v_topic_id IS NULL THEN
        RETURN NEW;
    END IF;

    -- Determine weight adjustment based on interaction type
    v_weight_delta := CASE NEW.interaction_type
        WHEN 'click' THEN 0.1
        WHEN 'read' THEN 0.2
        WHEN 'bookmark' THEN 0.5
        WHEN 'unbookmark' THEN -0.3
        WHEN 'share' THEN 0.6
        WHEN 'like' THEN 0.4
        WHEN 'dislike' THEN -1.0
        ELSE 0
    END;

    -- Get current weight or default to 1.0
    SELECT COALESCE(weight, 1.0) INTO v_current_weight
    FROM user_topics
    WHERE user_id = NEW.user_id AND topic_id = v_topic_id;

    -- Calculate new weight (clamped between 0.1 and 3.0)
    v_new_weight := GREATEST(0.1, LEAST(3.0, COALESCE(v_current_weight, 1.0) + v_weight_delta));

    -- Upsert the user topic weight
    INSERT INTO user_topics (user_id, topic_id, weight, selected_at)
    VALUES (NEW.user_id, v_topic_id, v_new_weight, NOW())
    ON CONFLICT (user_id, topic_id)
    DO UPDATE SET weight = v_new_weight;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to update weights on new interactions
CREATE TRIGGER trigger_update_topic_weight
    AFTER INSERT ON interactions
    FOR EACH ROW
    EXECUTE FUNCTION update_topic_weight_from_interaction();

-- Function for full-text search
CREATE OR REPLACE FUNCTION search_articles(
    p_query TEXT,
    p_limit INT DEFAULT 50
)
RETURNS TABLE (
    id UUID,
    title TEXT,
    preview TEXT,
    source_url TEXT,
    image_url TEXT,
    source_name TEXT,
    published_at TIMESTAMPTZ,
    primary_topic_id UUID,
    rank FLOAT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        a.id,
        a.title,
        a.preview,
        a.source_url,
        a.image_url,
        a.source_name,
        a.published_at,
        a.primary_topic_id,
        ts_rank(to_tsvector('english', a.title || ' ' || COALESCE(a.preview, '')), plainto_tsquery('english', p_query)) AS rank
    FROM articles a
    WHERE a.moderation_status = 'approved'
        AND to_tsvector('english', a.title || ' ' || COALESCE(a.preview, '')) @@ plainto_tsquery('english', p_query)
    ORDER BY rank DESC, a.published_at DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to find similar articles using vector embeddings
CREATE OR REPLACE FUNCTION find_similar_articles(
    p_article_id UUID,
    p_limit INT DEFAULT 10
)
RETURNS TABLE (
    id UUID,
    title TEXT,
    preview TEXT,
    image_url TEXT,
    source_name TEXT,
    similarity FLOAT
) AS $$
DECLARE
    v_embedding VECTOR(384);
BEGIN
    -- Get the embedding of the source article
    SELECT embedding INTO v_embedding
    FROM articles
    WHERE id = p_article_id;

    IF v_embedding IS NULL THEN
        RETURN;
    END IF;

    RETURN QUERY
    SELECT
        a.id,
        a.title,
        a.preview,
        a.image_url,
        a.source_name,
        1 - (a.embedding <=> v_embedding) AS similarity
    FROM articles a
    WHERE a.id != p_article_id
        AND a.embedding IS NOT NULL
        AND a.moderation_status = 'approved'
    ORDER BY a.embedding <=> v_embedding
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to get user statistics
CREATE OR REPLACE FUNCTION get_user_stats(p_user_id UUID)
RETURNS TABLE (
    articles_read INT,
    bookmarks_count INT,
    topics_followed INT,
    reading_time_minutes INT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        (SELECT COUNT(*)::INT FROM interactions WHERE user_id = p_user_id AND interaction_type = 'read'),
        (SELECT COUNT(*)::INT FROM interactions WHERE user_id = p_user_id AND interaction_type = 'bookmark'),
        (SELECT COUNT(*)::INT FROM user_topics WHERE user_id = p_user_id),
        (SELECT COALESCE(SUM(time_spent_seconds), 0)::INT / 60 FROM reading_sessions WHERE user_id = p_user_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
