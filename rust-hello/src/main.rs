use actix_web::middleware::Logger;
use actix_web::{web, App, HttpRequest, HttpServer};
use actix_web_opentelemetry::RequestTracing;
use opentelemetry::global::shutdown_tracer_provider;
use opentelemetry::sdk::propagation::TraceContextPropagator;
use opentelemetry::sdk::Resource;
use opentelemetry::{global, KeyValue};

async fn index(username: actix_web::web::Path<String>, req: HttpRequest) -> String {
    for i in req.headers().iter() {
        println!("header = {:?}", i);
    }
    greet_user(username.as_ref())
}

#[tracing::instrument]
fn greet_user(username: &str) -> String {
    tracing::info!("preparing to greet user");
    format!("Hello {}", username)
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    env_logger::init();
    global::set_text_map_propagator(TraceContextPropagator::new());
    let exporter = opentelemetry_otlp::new_exporter().tonic();
    opentelemetry_otlp::new_pipeline()
        .tracing()
        .with_trace_config(
            opentelemetry::sdk::trace::config().with_resource(Resource::new(vec![KeyValue::new(
                opentelemetry_semantic_conventions::resource::SERVICE_NAME,
                "jug-rust-service",
            )])),
        )
        .with_exporter(exporter)
        .install_simple()
        .expect("could not init the tracer");

    HttpServer::new(move || {
        App::new()
            .wrap(Logger::default())
            .wrap(RequestTracing::new())
            .service(web::resource("/person/id/{id}").to(index))
    })
    .bind("0.0.0.0:8081")
    .unwrap()
    .run()
    .await?;

    shutdown_tracer_provider();

    Ok(())
}
