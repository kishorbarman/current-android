// Supabase Edge Function: Get personalized feed
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS preflight
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      {
        global: {
          headers: { Authorization: req.headers.get('Authorization')! },
        },
      }
    )

    // Get authenticated user
    const {
      data: { user },
      error: authError,
    } = await supabaseClient.auth.getUser()

    if (authError || !user) {
      return new Response(
        JSON.stringify({ error: 'Unauthorized' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      )
    }

    // Parse query parameters
    const url = new URL(req.url)
    const limit = parseInt(url.searchParams.get('limit') ?? '50')
    const offset = parseInt(url.searchParams.get('offset') ?? '0')
    const topicId = url.searchParams.get('topic_id')

    let query = supabaseClient
      .from('articles')
      .select('*')
      .eq('moderation_status', 'approved')
      .order('published_at', { ascending: false })
      .range(offset, offset + limit - 1)

    if (topicId) {
      query = query.eq('primary_topic_id', topicId)
    }

    const { data: articles, error } = await query

    if (error) {
      throw error
    }

    // Get user's topic weights for scoring
    const { data: userTopics } = await supabaseClient
      .from('user_topics')
      .select('topic_id, weight')
      .eq('user_id', user.id)

    const topicWeights = new Map(
      userTopics?.map(ut => [ut.topic_id, ut.weight]) ?? []
    )

    // Calculate relevance scores
    const scoredArticles = articles?.map(article => {
      const topicWeight = topicWeights.get(article.primary_topic_id) ?? 0.5
      const ageHours = (Date.now() - new Date(article.published_at).getTime()) / (1000 * 60 * 60)
      const freshness = Math.exp(-ageHours / 48)

      const relevanceScore = topicWeight * 0.4 + freshness * 0.3 + Math.random() * 0.3

      return {
        ...article,
        relevance_score: relevanceScore,
      }
    })

    // Sort by relevance
    scoredArticles?.sort((a, b) => b.relevance_score - a.relevance_score)

    return new Response(
      JSON.stringify({ articles: scoredArticles, total: scoredArticles?.length ?? 0 }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})
