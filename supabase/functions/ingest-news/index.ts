// Supabase Edge Function: Ingest news from NewsAPI
// This should be called by a scheduled job (e.g., every 30 minutes)
import { serve } from 'https://deno.land/std@0.168.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const NEWS_API_KEY = Deno.env.get('NEWS_API_KEY')
const NEWS_API_BASE = 'https://newsapi.org/v2'

const CATEGORY_TO_TOPIC: Record<string, string> = {
  'technology': 'a1b2c3d4-e5f6-4789-abcd-ef0123456789',
  'science': 'b2c3d4e5-f6a7-4890-bcde-f01234567890',
  'business': 'c3d4e5f6-a7b8-4901-cdef-012345678901',
  'sports': 'd4e5f6a7-b8c9-4012-def0-123456789012',
  'entertainment': 'e5f6a7b8-c9d0-4123-ef01-234567890123',
  'health': 'f6a7b8c9-d0e1-4234-f012-345678901234',
}

interface NewsApiArticle {
  source: { id: string | null; name: string }
  author: string | null
  title: string
  description: string | null
  url: string
  urlToImage: string | null
  publishedAt: string
  content: string | null
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    // Verify service role for scheduled jobs
    const authHeader = req.headers.get('Authorization')
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

    if (!authHeader?.includes(serviceRoleKey ?? '')) {
      // For testing, also allow with a secret header
      const secretHeader = req.headers.get('X-Ingest-Secret')
      if (secretHeader !== Deno.env.get('INGEST_SECRET')) {
        return new Response(
          JSON.stringify({ error: 'Unauthorized' }),
          { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        )
      }
    }

    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const categories = Object.keys(CATEGORY_TO_TOPIC)
    let totalIngested = 0
    const errors: string[] = []

    for (const category of categories) {
      try {
        const response = await fetch(
          `${NEWS_API_BASE}/top-headlines?country=us&category=${category}&pageSize=20`,
          {
            headers: {
              'X-Api-Key': NEWS_API_KEY ?? '',
            },
          }
        )

        if (!response.ok) {
          errors.push(`Failed to fetch ${category}: ${response.statusText}`)
          continue
        }

        const data = await response.json()
        const articles: NewsApiArticle[] = data.articles ?? []

        const articlesToInsert = articles
          .filter(a => a.title && a.url && !a.title.includes('[Removed]'))
          .map(article => ({
            title: article.title,
            preview: article.description,
            content: article.content,
            source_url: article.url,
            image_url: article.urlToImage,
            source_name: article.source.name,
            author: article.author,
            published_at: article.publishedAt,
            primary_topic_id: CATEGORY_TO_TOPIC[category],
            moderation_status: 'approved',
          }))

        if (articlesToInsert.length > 0) {
          const { error } = await supabaseAdmin
            .from('articles')
            .upsert(articlesToInsert, {
              onConflict: 'source_url',
              ignoreDuplicates: true
            })

          if (error) {
            errors.push(`Error inserting ${category}: ${error.message}`)
          } else {
            totalIngested += articlesToInsert.length
          }
        }
      } catch (err) {
        errors.push(`Exception for ${category}: ${err.message}`)
      }
    }

    return new Response(
      JSON.stringify({
        message: 'News ingestion complete',
        ingested: totalIngested,
        errors: errors.length > 0 ? errors : undefined
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )
  }
})
