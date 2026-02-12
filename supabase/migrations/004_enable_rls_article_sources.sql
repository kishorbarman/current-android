-- Enable RLS on article_sources (was missed in initial migration)
-- This is an internal table managed by edge functions using the service_role key,
-- so no public access policies are needed.

ALTER TABLE article_sources ENABLE ROW LEVEL SECURITY;
