-- Seed default topics

INSERT INTO topics (id, name, slug, icon) VALUES
    ('a1b2c3d4-e5f6-4789-abcd-ef0123456789', 'Technology', 'technology', 'computer'),
    ('b2c3d4e5-f6a7-4890-bcde-f01234567890', 'Science', 'science', 'science'),
    ('c3d4e5f6-a7b8-4901-cdef-012345678901', 'Business', 'business', 'business'),
    ('d4e5f6a7-b8c9-4012-def0-123456789012', 'Sports', 'sports', 'sports'),
    ('e5f6a7b8-c9d0-4123-ef01-234567890123', 'Entertainment', 'entertainment', 'movie'),
    ('f6a7b8c9-d0e1-4234-f012-345678901234', 'Health', 'health', 'health'),
    ('a7b8c9d0-e1f2-4345-0123-456789012345', 'Politics', 'politics', 'gavel'),
    ('b8c9d0e1-f2a3-4456-1234-567890123456', 'World News', 'world', 'public'),
    ('c9d0e1f2-a3b4-4567-2345-678901234567', 'Gaming', 'gaming', 'gamepad'),
    ('d0e1f2a3-b4c5-4678-3456-789012345678', 'Food', 'food', 'restaurant'),
    ('e1f2a3b4-c5d6-4789-4567-890123456789', 'Travel', 'travel', 'flight'),
    ('f2a3b4c5-d6e7-4890-5678-901234567890', 'Finance', 'finance', 'trending_up')
ON CONFLICT (slug) DO NOTHING;

-- Add some sub-topics
INSERT INTO topics (name, slug, icon, parent_topic_id) VALUES
    ('Artificial Intelligence', 'ai', 'smart_toy', 'a1b2c3d4-e5f6-4789-abcd-ef0123456789'),
    ('Smartphones', 'smartphones', 'smartphone', 'a1b2c3d4-e5f6-4789-abcd-ef0123456789'),
    ('Cybersecurity', 'cybersecurity', 'security', 'a1b2c3d4-e5f6-4789-abcd-ef0123456789'),
    ('Space', 'space', 'rocket', 'b2c3d4e5-f6a7-4890-bcde-f01234567890'),
    ('Climate', 'climate', 'eco', 'b2c3d4e5-f6a7-4890-bcde-f01234567890'),
    ('Startups', 'startups', 'lightbulb', 'c3d4e5f6-a7b8-4901-cdef-012345678901'),
    ('Cryptocurrency', 'crypto', 'currency_bitcoin', 'f2a3b4c5-d6e7-4890-5678-901234567890')
ON CONFLICT (slug) DO NOTHING;
