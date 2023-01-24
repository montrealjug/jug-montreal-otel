use actix_web::middleware::Logger;
use actix_web::{web, App, HttpServer};
use actix_web_opentelemetry::RequestTracing;
use opentelemetry::{global, sdk::propagation::TraceContextPropagator};
use std::io;
use tracing_subscriber::prelude::*;
use tracing_subscriber::Registry;

async fn index(username: actix_web::web::Path<String>) -> String {
    greet_user(username.as_ref())
}

#[tracing::instrument]
fn greet_user(id: &str) -> String {
    tracing::info!("preparing to greet user");
    format!("Hello id = {}", id)
}

#[actix_web::main]
async fn main() -> io::Result<()> {
    global::set_text_map_propagator(TraceContextPropagator::new());
    let tracer = opentelemetry_jaeger::new_pipeline()
        .with_service_name("jug-rust-hello")
        .install_simple()
        .unwrap();

    Registry::default()
        .with(tracing_subscriber::EnvFilter::new("INFO"))
        .with(tracing_subscriber::fmt::layer())
        .with(tracing_opentelemetry::layer().with_tracer(tracer))
        .init();

    HttpServer::new(move || {
        App::new()
            .wrap(Logger::default())
            .wrap(RequestTracing::new())
            .service(web::resource("/person/id/{id}").to(index))
    })
    .bind("0.0.0.0:8081")?
    .run()
    .await
}
