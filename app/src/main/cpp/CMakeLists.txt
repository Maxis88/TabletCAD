cmake_minimum_required(VERSION 3.22)
project(tabletcad_native LANGUAGES CXX)

add_library(tabletcad SHARED native_cad_engine.cpp)
target_compile_features(tabletcad PRIVATE cxx_std_20)
target_compile_options(tabletcad PRIVATE -Wall -Wextra -Werror)
get_filename_component(TABLETCAD_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(OCCT_BUILD "${TABLETCAD_ROOT}/build/occt-arm64")
set(OCCT_LIB_DIR "${OCCT_BUILD}/lin64/clang/lib")
set(OCCT_LIBRARIES
    TKDESTEP TKDE TKXCAF TKXSBase TKVCAF TKCAF TKLCAF TKCDF TKFillet TKBool TKBO
    TKPrim TKV3d TKService TKMesh TKShHealing TKHLR TKTopAlgo
    TKGeomAlgo TKBRep TKGeomBase TKG3d TKG2d TKMath TKernel)

foreach(OCCT_LIBRARY IN LISTS OCCT_LIBRARIES)
    add_library(${OCCT_LIBRARY} SHARED IMPORTED)
    set_target_properties(${OCCT_LIBRARY} PROPERTIES
        IMPORTED_LOCATION "${OCCT_LIB_DIR}/lib${OCCT_LIBRARY}.so")
endforeach()

target_include_directories(tabletcad PRIVATE "${OCCT_BUILD}/include/opencascade")
target_link_libraries(tabletcad PRIVATE ${OCCT_LIBRARIES})
set_target_properties(tabletcad PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON)
