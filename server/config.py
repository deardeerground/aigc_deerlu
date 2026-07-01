from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    server_host: str = "0.0.0.0"
    server_port: int = 8000

    database_url: str = "postgresql+asyncpg://huoyejia:huoyejia@localhost:5432/huoyejia"

    llm_chat_base_url: str = ""
    llm_chat_api_key: str = ""
    llm_chat_model: str = ""

    llm_embedding_base_url: str = ""
    llm_embedding_api_key: str = ""
    llm_embedding_model: str = ""
    llm_embedding_path: str = "/embeddings"

    llm_image_base_url: str = ""
    llm_image_api_key: str = ""
    llm_image_model: str = ""
    llm_image_path: str = "/images/generations"
    llm_image_size: str = "1024x1024"

    video_base_url: str = ""
    video_api_key: str = ""
    video_model: str = ""
    video_create_path: str = "/videos/generations"
    video_status_path: str = "/videos/{id}"
    video_generate_audio: bool = True
    video_ratio: str = "16:9"
    video_duration: int = 10
    video_watermark: bool = False

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
