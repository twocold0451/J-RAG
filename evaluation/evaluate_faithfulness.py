import os
import yaml
import json
import psycopg2
from datasets import Dataset
from ragas import evaluate
from ragas.metrics import faithfulness
from langchain_openai import ChatOpenAI
from langchain_openai import OpenAIEmbeddings

def load_config(config_path="config.yaml"):
    with open(config_path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)

def fetch_data(config):
    db_config = config['database']
    sampling_config = config['sampling']
    
    conn = psycopg2.connect(
        host=db_config['host'],
        port=db_config['port'],
        dbname=db_config['name'],
        user=db_config['user'],
        password=db_config['password'],
        options=f"-c search_path={db_config.get('schema', 'public')}"
    )
    
    query = """
        SELECT user_query, ai_response, retrieved_contexts 
        FROM rag_interactions 
        WHERE user_query IS NOT NULL 
          AND ai_response IS NOT NULL 
          AND retrieved_contexts IS NOT NULL
        ORDER BY created_at DESC 
        LIMIT %s
    """
    
    cursor = conn.cursor()
    cursor.execute(query, (sampling_config.get('limit', 20),))
    rows = cursor.fetchall()
    conn.close()
    
    data = {
        'question': [],
        'answer': [],
        'contexts': [] # Ragas expects a list of strings for contexts
    }
    
    for row in rows:
        user_query, ai_response, retrieved_contexts_json = row
        
        # Parse retrieved_contexts
        # Assuming retrieved_contexts is a list of objects with a 'content' field based on SQL comment
        # If it's already a list of strings, use as is.
        contexts = []
        if retrieved_contexts_json:
            if isinstance(retrieved_contexts_json, str):
                try:
                    loaded = json.loads(retrieved_contexts_json)
                except:
                    loaded = []
            else:
                loaded = retrieved_contexts_json
            
            if isinstance(loaded, list):
                for item in loaded:
                    if isinstance(item, dict) and 'content' in item:
                        contexts.append(item['content'])
                    elif isinstance(item, str):
                        contexts.append(item)
        
        # Only add if we have contexts (faithfulness requires contexts)
        if contexts:
            data['question'].append(user_query)
            data['answer'].append(ai_response)
            data['contexts'].append(contexts)
            
    return data

def main():
    # 1. Load Config
    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(script_dir, "config.yaml")
    config = load_config(config_path)
    
    # 2. Set OpenAI Key for Ragas/LangChain
    os.environ["OPENAI_API_KEY"] = config['openai']['api_key']
    
    # 3. Fetch Data from DB
    print(f"Connecting to database {config['database']['name']}...")
    raw_data = fetch_data(config)
    print(f"Fetched {len(raw_data['question'])} interaction records for evaluation.")

    if len(raw_data['question']) == 0:
        print("No data found to evaluate.")
        return

    # 4. Prepare Dataset
    dataset = Dataset.from_dict(raw_data)
    
    # 5. Configure LLM (Optional explicitly, but good for custom base_url/models)
    # Ragas uses LangChain LLMs.
    llm = ChatOpenAI(
        model=config['openai']['model'],
        base_url=config['openai'].get('base_url'),
        api_key=config['openai']['api_key']
    )
    
    # Ragas might need embeddings for some metrics, though faithfulness is usually LLM based.
    # We'll configure it just in case or for future extensibility.
    embeddings = OpenAIEmbeddings(
        base_url=config['openai'].get('base_url'),
        api_key=config['openai']['api_key']
    )

    # 6. Run Evaluation
    print("Running evaluation (faithfulness)...")
    results = evaluate(
        dataset=dataset,
        metrics=[faithfulness],
        llm=llm,
        embeddings=embeddings
    )
    
    # 7. Output Results
    print("\nEvaluation Results:")
    print(results)
    
    # Save detailed results to CSV
    df = results.to_pandas()
    output_file = os.path.join(script_dir, "evaluation_results.csv")
    df.to_csv(output_file, index=False)
    print(f"\nDetailed results saved to: {output_file}")

if __name__ == "__main__":
    main()
