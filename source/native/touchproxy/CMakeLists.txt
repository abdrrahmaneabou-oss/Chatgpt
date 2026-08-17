cmake_minimum_required(VERSION 3.22)
project(pixeltrigger_touchproxy LANGUAGES CXX)

add_executable(touchproxy touchproxy.cpp)
target_compile_features(touchproxy PRIVATE cxx_std_20)
target_compile_options(touchproxy PRIVATE -Wall -Wextra -Werror)

if(ANDROID)
    target_compile_definitions(touchproxy PRIVATE PIXELTRIGGER_ANDROID=1)
    target_link_options(touchproxy PRIVATE -Wl,--gc-sections)
endif()
